import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Adds death message metadata at the original damage calls in the pinned SRG build. */
public final class PatchKillMessages implements Opcodes {
    private static final String MESSAGES = "cn/ssjjcompat/CombatMessages";
    private static final String STRING = "Ljava/lang/String;";
    private static final String COMPONENT = "Lnet/minecraft/network/chat/Component;";
    private static final String LEVEL_PLAYER = "Lnet/minecraft/server/level/ServerPlayer;";
    private static final String LIVING = "Lnet/minecraft/world/entity/LivingEntity;";
    private static final String ENTITY = "Lnet/minecraft/world/entity/Entity;";
    private static final String SOURCE = "Lnet/minecraft/world/damagesource/DamageSource;";
    private static final String SOURCES = "net/minecraft/world/damagesource/DamageSources";
    private static final String FIELD = "cn/ssjjdimension/DimensionField";
    private static final String SNAPSHOT = "Lcn/ssjjdimension/DimensionAttachments$Snapshot;";
    private static final String FIELD_DAMAGE = "(" + LIVING + LIVING + "F" + SNAPSHOT + ")Z";
    private static final String DARK = "cn/ssjjdark/DarkCombat";
    private static final String DARK_DAMAGE = "(" + LEVEL_PLAYER + LIVING + "F)V";
    private static final String DARK_HIT = "(" + LEVEL_PLAYER + "Lcn/ssjjdark/DarkDamageRules$Strike;)V";
    private static final String BLAST = "cn/ssjjzeus/ZeusThunder$NativeBlast";
    private static final String VEC = "Lnet/minecraft/world/phys/Vec3;";

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]);
        try (var baseline = new ZipFile(args[0])) {
            patch(baseline, output, "cn/punishmenteye/EyeSkillDamage", type ->
                delegate(type, "eye", "(" + COMPONENT.repeat(2) + "Z)" + COMPONENT, ALOAD, ALOAD, ILOAD));
            patch(baseline, output, "cn/judgfire/WeaponDamage", type ->
                delegate(type, "flame", "(" + COMPONENT.repeat(3) + "Z)" + COMPONENT, ALOAD, ALOAD, ALOAD, ILOAD));
            patch(baseline, output, FIELD, PatchKillMessages::dimension);
            patch(baseline, output, DARK, PatchKillMessages::dark);
            patch(baseline, output, "cn/ssjjdark/DarkDragon", type ->
                damageMode(method(type, "advance", "(Lcn/ssjjdark/DarkDragon$Wave;)Z"), DARK, DARK_DAMAGE, "dark_dragon"));
            patch(baseline, output, "cn/ssjjdark/DarkLiberation", type ->
                damageMode(method(type, "explode", "(Lcn/ssjjdark/DarkLiberation$Session;)V"),
                    DARK, DARK_DAMAGE, "dark_liberation"));
            patch(baseline, output, "cn/ssjjhyper/HyperCharge", type -> {
                var damage = method(type, "damage", "(" + LIVING + LIVING + "F)Z");
                recordSource(damage, "m_269104_", "(" + ENTITY + ENTITY + ")" + SOURCE,
                    new LdcInsnNode("hyper_charge"));
            });
            patch(baseline, output, BLAST, PatchKillMessages::zeus);
        }
    }

    private static void patch(ZipFile baseline, Path output, String name, Consumer<ClassNode> transform)
            throws Exception {
        var entry = baseline.getEntry(name + ".class");
        if (entry == null) throw new IllegalStateException("Missing class " + name);
        var type = new ClassNode();
        try (var stream = baseline.getInputStream(entry)) {
            new ClassReader(stream).accept(type, ClassReader.EXPAND_FRAMES);
        }
        transform.accept(type);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        var file = output.resolve(entry.getName());
        Files.createDirectories(file.getParent());
        Files.write(file, writer.toByteArray());
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        return owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing method " + owner.name + "." + name + desc));
    }

    private static <T extends AbstractInsnNode> T anchor(MethodNode method, Class<T> type, Predicate<T> matches) {
        var found = new ArrayList<T>();
        for (var next : method.instructions) {
            if (type.isInstance(next) && matches.test(type.cast(next))) found.add(type.cast(next));
        }
        if (found.size() != 1)
            throw new IllegalStateException(method.name + method.desc + ": expected one anchor, found " + found.size());
        return found.get(0);
    }

    private static MethodInsnNode call(MethodNode method, String owner, String name, String desc) {
        return anchor(method, MethodInsnNode.class, c -> c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc));
    }

    private static InsnList instructions(AbstractInsnNode... nodes) {
        var list = new InsnList();
        for (var node : nodes) list.add(node);
        return list;
    }

    private static MethodInsnNode messages(String method, String desc) {
        return new MethodInsnNode(INVOKESTATIC, MESSAGES, method, desc, false);
    }

    private static void delegate(ClassNode type, String entry, String desc, int... loads) {
        var method = method(type, "message", desc);
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        for (int index = 0; index < loads.length; index++)
            method.instructions.add(new VarInsnNode(loads[index], index));
        method.instructions.add(messages(entry, desc));
        method.instructions.add(new InsnNode(ARETURN));
    }

    private static String withMode(String desc) {
        int end = desc.indexOf(')');
        return desc.substring(0, end) + STRING + desc.substring(end);
    }

    // Preserve the compiled gameplay body and its frames while adding the style parameter.
    private static void addModeParameter(MethodNode method, int slot) {
        if ((method.access & ACC_STATIC) == 0) throw new IllegalStateException(method.name + " must be static");
        method.desc = withMode(method.desc);
        for (var instruction : method.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.var >= slot) variable.var++;
            if (instruction instanceof IincInsnNode increment && increment.var >= slot) increment.var++;
            if (instruction instanceof FrameNode frame) {
                if (frame.type != F_NEW || frame.local.size() < slot)
                    throw new IllegalStateException("Unexpected locals in " + method.name);
                frame.local.add(slot, "java/lang/String");
            }
        }
        if (method.localVariables != null) {
            for (var variable : method.localVariables) if (variable.index >= slot) variable.index++;
        }
    }

    private static void modeAtCall(MethodNode method, String owner, String name, String desc, InsnList mode) {
        var target = call(method, owner, name, desc);
        method.instructions.insertBefore(target, mode);
        target.desc = withMode(desc);
    }

    private static void damageMode(MethodNode method, String owner, String desc, String mode) {
        modeAtCall(method, owner, "damage", desc, instructions(new LdcInsnNode(mode)));
    }

    private static void recordSource(MethodNode method, String factory, String desc, AbstractInsnNode mode) {
        var source = call(method, SOURCES, factory, desc);
        method.instructions.insert(source, instructions(mode, messages("remember", "(" + SOURCE + STRING + ")" + SOURCE)));
    }

    private static void dimension(ClassNode type) {
        var damage = method(type, "damage", FIELD_DAMAGE);
        addModeParameter(damage, 4);
        var constant = anchor(damage, LdcInsnNode.class, load -> "dimension_field".equals(load.cst));
        damage.instructions.set(constant, new VarInsnNode(ALOAD, 4));
        damageMode(method(type, "decoyBlast", "(" + LEVEL_PLAYER + LIVING + STRING + SNAPSHOT + ")Z"),
            FIELD, FIELD_DAMAGE, "dimension_decoy");
        damageMode(method(type, "blast", "(Lnet/minecraft/server/level/ServerLevel;L" + FIELD + "$Area;" + LIVING + ")V"),
            FIELD, FIELD_DAMAGE, "dimension_field");
        damageMode(method(type, "onMarkExplosion", "(" + LIVING + LIVING + ")V"),
            FIELD, FIELD_DAMAGE, "dimension_field_mark");
    }

    private static void dark(ClassNode type) {
        var damage = method(type, "damage", DARK_DAMAGE);
        addModeParameter(damage, 3);
        recordSource(damage, "m_269075_", "(Lnet/minecraft/world/entity/player/Player;)" + SOURCE,
            new VarInsnNode(ALOAD, 3));
        // Keep the old package-level entry point for other code compiled against it.
        var bridge = new MethodNode(ASM9, ACC_STATIC, "damage", DARK_DAMAGE, null, null);
        bridge.instructions.add(instructions(new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, 1),
            new VarInsnNode(FLOAD, 2), new LdcInsnNode("dark_slash"),
            new MethodInsnNode(INVOKESTATIC, DARK, "damage", withMode(DARK_DAMAGE), false), new InsnNode(RETURN)));
        type.methods.add(bridge);

        var hit = method(type, "hit", DARK_HIT);
        addModeParameter(hit, 2);
        modeAtCall(hit, DARK, "damage", DARK_DAMAGE, instructions(new VarInsnNode(ALOAD, 2)));
        var tick = method(type, "tickDarkCrystalCombat", "(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V");
        var pending = anchor(tick, FieldInsnNode.class, field ->
            field.getOpcode() == GETFIELD && field.owner.equals(DARK + "$Pending") && field.name.equals("id"));
        var pendingLoad = pending.getPrevious();
        if (!(pendingLoad instanceof VarInsnNode load) || load.getOpcode() != ALOAD)
            throw new IllegalStateException("Missing pending strike at the dark crystal tick");
        modeAtCall(tick, DARK, "hit", DARK_HIT, instructions(new VarInsnNode(ALOAD, load.var),
            new MethodInsnNode(INVOKEVIRTUAL, DARK + "$Pending", "heavy", "()Z", false),
            messages("darkStrike", "(Z)" + STRING)));
    }

    private static void zeus(ClassNode type) {
        var chain = method(type, "chain", "(F)L" + BLAST + ";");
        var result = anchor(chain, InsnNode.class, node -> node.getOpcode() == ARETURN);
        chain.instructions.insertBefore(result, instructions(new InsnNode(DUP), new LdcInsnNode("zeus_chain"),
            messages("rememberProjectile", "(" + ENTITY + STRING + ")V")));
        var hit = method(type, "hit", "(" + ENTITY + VEC.repeat(3) + ")V");
        hit.instructions.insert(instructions(new VarInsnNode(ALOAD, 0), new LdcInsnNode("zeus_burst"),
            messages("rememberProjectileIfAbsent", "(" + ENTITY + STRING + ")V")));
    }
}
