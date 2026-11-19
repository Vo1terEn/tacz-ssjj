package cn.fusionarsenal.health;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Identifies a foreign health pool from an exact getter expression. No field-name
 * heuristics, unrelated numeric writes, entity removal or death handling occurs here.
 * The caller owns the server-side damage/death scope.
 */
public final class PrivateHealthProbe {
    private PrivateHealthProbe() {}

    /** An explicit integration for pools whose semantics cannot be inferred safely. */
    public interface Adapter {
        float read(LivingEntity target) throws Exception;
        boolean write(LivingEntity target, float value) throws Exception;
    }

    private static final class Slot {
        final Adapter discovered;
        volatile Adapter registered;
        Slot(Adapter discovered) { this.discovered = discovered; }
        Adapter adapter() { return registered != null ? registered : discovered; }
    }

    // ClassValue does not pin optional mods' class loaders or retain entity instances.
    private static final ClassValue<Slot> PLANS = new ClassValue<>() {
        @Override protected Slot computeValue(Class<?> type) {
            return new Slot(discover(type));
        }
    };

    /** Registers an adapter for this exact runtime class, not arbitrary subclasses. */
    public static void register(Class<? extends LivingEntity> type, Adapter adapter) {
        PLANS.get(Objects.requireNonNull(type)).registered = Objects.requireNonNull(adapter);
    }

    public static void unregister(Class<? extends LivingEntity> type) {
        PLANS.get(Objects.requireNonNull(type)).registered = null;
    }

    public static float read(LivingEntity target, float vanilla) {
        if (target == null) return vanilla;
        Adapter adapter = PLANS.get(target.getClass()).adapter();
        if (adapter == null) return vanilla;
        try {
            float result = adapter.read(target);
            return Float.isFinite(result) && result >= 0 ? result : vanilla;
        } catch (Exception | LinkageError ignored) {
            return vanilla;
        }
    }

    public static boolean write(LivingEntity target, float value) {
        if (target == null || !Float.isFinite(value) || value < 0) return false;
        Adapter adapter = PLANS.get(target.getClass()).adapter();
        if (adapter == null) return false;
        try {
            return adapter.write(target, value);
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    private static Adapter discover(Class<?> type) {
        try {
            for (Class<?> owner = type; owner != null && owner != LivingEntity.class;
                 owner = owner.getSuperclass()) {
                String resource = "/" + owner.getName().replace('.', '/') + ".class";
                try (InputStream bytes = owner.getResourceAsStream(resource)) {
                    if (bytes == null) return null;
                    ClassNode node = new ClassNode();
                    new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    for (MethodNode method : node.methods) {
                        if (!isHealthGetter(method)) continue;
                        // The first override controls dispatch. Never infer a parent's
                        // storage through an opaque arithmetic/constant/method override.
                        if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT |
                                             Opcodes.ACC_NATIVE)) != 0) return null;
                        return parse(owner, method);
                    }
                }
            }
        } catch (Exception | LinkageError ignored) {
            // Optional mod classes or inaccessible layouts degrade to native storage.
        }
        return null;
    }

    private static boolean isHealthGetter(MethodNode method) {
        return (method.name.equals("getHealth") || method.name.equals("m_21223_"))
            && (method.desc.equals("()F") || method.desc.equals("()D"));
    }

    private static Adapter parse(Class<?> owner, MethodNode method) throws Exception {
        if (!method.tryCatchBlocks.isEmpty()) return null;
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction.getOpcode() >= 0) code.add(instruction);
        if (code.isEmpty() || !self(code.get(0))) return null;
        int last = code.size() - 1;
        int returnOpcode = method.desc.equals("()F") ? Opcodes.FRETURN : Opcodes.DRETURN;
        if (code.get(last).getOpcode() != returnOpcode) return null;

        if (code.size() >= 3 && code.get(1) instanceof FieldInsnNode field
            && field.getOpcode() == Opcodes.GETFIELD
            && (field.desc.equals("F") || field.desc.equals("D"))
            && numericTail(code, 2, field.desc.equals("F"), method.desc.equals("()F"))) {
            Field storage = resolve(owner, field);
            if (Modifier.isStatic(storage.getModifiers()) || Modifier.isFinal(storage.getModifiers())) return null;
            if (storage.getType() != (field.desc.equals("F") ? float.class : double.class)) return null;
            return new Adapter() {
                public float read(LivingEntity target) throws IllegalAccessException {
                    double value = storage.getType() == float.class ? storage.getFloat(target) : storage.getDouble(target);
                    return (float) Math.min(Float.MAX_VALUE, value);
                }
                public boolean write(LivingEntity target, float value) throws IllegalAccessException {
                    if (storage.getType() == float.class) storage.setFloat(target, value);
                    else storage.setDouble(target, value);
                    return true;
                }
            };
        }

        // this.getEntityData().get(STATIC_KEY), followed only by numeric unboxing
        // and an optional float/double conversion. The receiver and accessor are exact.
        if (code.size() < 7 || !(code.get(1) instanceof MethodInsnNode data)
            || data.getOpcode() != Opcodes.INVOKEVIRTUAL
            || !(data.name.equals("getEntityData") || data.name.equals("m_20088_"))
            || !data.desc.equals("()Lnet/minecraft/network/syncher/SynchedEntityData;")
            || !(code.get(2) instanceof FieldInsnNode key)
            || key.getOpcode() != Opcodes.GETSTATIC
            || !key.desc.equals("Lnet/minecraft/network/syncher/EntityDataAccessor;")
            || !(code.get(3) instanceof MethodInsnNode get)
            || get.getOpcode() != Opcodes.INVOKEVIRTUAL
            || !get.owner.equals("net/minecraft/network/syncher/SynchedEntityData")
            || !(get.name.equals("get") || get.name.equals("m_135370_"))
            || !get.desc.equals("(Lnet/minecraft/network/syncher/EntityDataAccessor;)Ljava/lang/Object;")
            || !(code.get(4) instanceof TypeInsnNode cast)
            || cast.getOpcode() != Opcodes.CHECKCAST
            || !(cast.desc.equals("java/lang/Float") || cast.desc.equals("java/lang/Double")
                 || cast.desc.equals("java/lang/Number"))
            || !(code.get(5) instanceof MethodInsnNode unbox)
            || unbox.getOpcode() != Opcodes.INVOKEVIRTUAL || !unbox.owner.equals(cast.desc)) return null;
        boolean fromFloat = unbox.name.equals("floatValue") && unbox.desc.equals("()F");
        boolean fromDouble = unbox.name.equals("doubleValue") && unbox.desc.equals("()D");
        if ((!fromFloat && !fromDouble) || !numericTail(code, 6, fromFloat, method.desc.equals("()F"))) return null;
        Field accessorField = resolve(owner, key);
        if (!Modifier.isStatic(accessorField.getModifiers())) return null;
        Object accessor = accessorField.get(null);
        if (!(accessor instanceof EntityDataAccessor<?>)) return null;
        return dataAdapter((EntityDataAccessor<?>) accessor);
    }

    private static boolean self(AbstractInsnNode node) {
        return node instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == 0;
    }

    private static boolean numericTail(List<AbstractInsnNode> code, int start, boolean fromFloat, boolean toFloat) {
        int tail = code.size() - 1;
        if (fromFloat == toFloat) return start == tail;
        return start + 1 == tail
            && code.get(start).getOpcode() == (fromFloat ? Opcodes.F2D : Opcodes.D2F);
    }

    private static Field resolve(Class<?> context, FieldInsnNode field) throws Exception {
        Class<?> owner = Class.forName(field.owner.replace('/', '.'), false, context.getClassLoader());
        // An instance slot must belong to this entity's ancestry; no foreign object traversal.
        if (field.getOpcode() == Opcodes.GETFIELD && !owner.isAssignableFrom(context))
            throw new IllegalArgumentException("Non-entity health slot");
        // javac may encode a subclass as the symbolic owner of an inherited slot.
        // Resolve that exact field name through its ancestors, as GETFIELD does.
        for (Class<?> declaring = owner; declaring != null; declaring = declaring.getSuperclass()) {
            try {
                Field result = declaring.getDeclaredField(field.name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + field.name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Adapter dataAdapter(EntityDataAccessor accessor) {
        return new Adapter() {
            public float read(LivingEntity target) {
                Object value = target.getEntityData().get(accessor);
                return value instanceof Float || value instanceof Double
                    ? (float) Math.min(Float.MAX_VALUE, ((Number) value).doubleValue()) : Float.NaN;
            }
            public boolean write(LivingEntity target, float value) {
                Object before = target.getEntityData().get(accessor);
                if (before instanceof Float) target.getEntityData().set(accessor, Float.valueOf(value));
                else if (before instanceof Double) target.getEntityData().set(accessor, Double.valueOf(value));
                else return false;
                Object after = target.getEntityData().get(accessor);
                return after instanceof Number number && number.doubleValue() == value;
            }
        };
    }
}
