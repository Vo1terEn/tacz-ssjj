package cn.fusionarsenal.health;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.objectweb.asm.Opcodes.*;

/** Observes only counters written by the entity's death tick. Never mutates a timer. */
public final class DeathProgressProbe {
    private DeathProgressProbe() {}
    private record Counter(Field field, String id) {}
    private static final ClassValue<List<Counter>> COUNTERS = new ClassValue<>() {
        protected List<Counter> computeValue(Class<?> type) {
            Map<String, Counter> counters = new LinkedHashMap<>();
            Set<String> visited = new HashSet<>();
            for (Class<?> owner = type; owner != null && LivingEntity.class.isAssignableFrom(owner); owner = owner.getSuperclass()) {
                ClassNode node = read(owner);
                for (MethodNode method : node.methods) {
                    if ((method.name.equals("tickDeath") || method.name.equals("m_6153_")) && method.desc.equals("()V"))
                        scan(type, owner, method, 0, visited, counters);
                }
            }
            return List.copyOf(counters.values());
        }
    };
    public static Map<String, Double> sample(LivingEntity target) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Counter counter : COUNTERS.get(target.getClass())) {
            try {
                Object raw = counter.field.get(target);
                if (raw instanceof Number number && Double.isFinite(number.doubleValue()))
                    values.put(counter.id, number.doubleValue());
            } catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        return values;
    }
    private static void scan(Class<?> type, Class<?> owner, MethodNode method, int depth,
                             Set<String> visited, Map<String, Counter> counters) {
        if (depth > 4 || visited.size() >= 48 || method.instructions.size() > 4096
            || !visited.add(owner.getName() + "." + method.name + method.desc)) return;
        for (AbstractInsnNode instruction : method.instructions) {
            try {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == PUTFIELD
                    && (field.desc.equals("I") || field.desc.equals("J") || field.desc.equals("F") || field.desc.equals("D"))) {
                    Class<?> declaring = Class.forName(field.owner.replace('/', '.'), false, type.getClassLoader());
                    if (!declaring.isAssignableFrom(type)) continue;
                    Field reflected = findField(declaring, field.name);
                    if (reflected == null || Modifier.isStatic(reflected.getModifiers()) || !reflected.trySetAccessible()) continue;
                    String id = reflected.getDeclaringClass().getName() + "." + reflected.getName();
                    if (counters.size() < 48) counters.putIfAbsent(id, new Counter(reflected, id));
                } else if (instruction instanceof MethodInsnNode call && !call.name.startsWith("<")) {
                    Class<?> declaring = Class.forName(call.owner.replace('/', '.'), false, type.getClassLoader());
                    if (!declaring.isAssignableFrom(type) || !LivingEntity.class.isAssignableFrom(declaring)) continue;
                    // Only traverse entity-local helper bodies. Level, AI and unrelated object ticks are excluded.
                    for (MethodNode helper : read(declaring).methods)
                        if (helper.name.equals(call.name) && helper.desc.equals(call.desc))
                            scan(type, declaring, helper, depth + 1, visited, counters);
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {}
        }
    }
    private static Field findField(Class<?> owner, String name) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try { return type.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
    private static final ClassValue<ClassNode> CLASSES = new ClassValue<>() {
        protected ClassNode computeValue(Class<?> type) {
            ClassNode node = new ClassNode();
            try {
                byte[] bytes = ClassSnapshotAccess.read(type);
                if (bytes != null && bytes.length <= 2 * 1024 * 1024)
                    new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (Exception | LinkageError ignored) {}
            return node;
        }
    };
    private static ClassNode read(Class<?> type) { return CLASSES.get(type); }
}
