import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Preserves the production class bodies and adds one entry guard to each energy evaluator. */
public final class PatchAttributeOwnerGuard implements Opcodes {
    private static final String HELPER = "cn/ssjjcompat/AttributeOwnerCompat";
    private static final String ENTITY = "net/minecraft/world/entity/LivingEntity";
    private static final String ATTRIBUTES = "()Lnet/minecraft/world/entity/ai/attributes/AttributeMap;";
    private static final Set<String> GET_INSTANCE = Set.of("getAttribute", "m_21051_", "getInstance", "m_22146_");

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]), overlay = Path.of(args[1]), report = Path.of(args[2]);
        StringBuilder audit = new StringBuilder("Production lazy attribute access call sites:\n");
        try (ZipFile source = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(source.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                new ClassReader(source.getInputStream(entry).readAllBytes()).accept(node, 0);
                for (MethodNode method : node.methods) {
                    for (AbstractInsnNode insn : method.instructions) {
                        if (insn instanceof MethodInsnNode call && GET_INSTANCE.contains(call.name)
                                && call.desc.contains("ai/attributes/Attribute;")) {
                            audit.append(node.name).append('.').append(method.name).append(method.desc)
                                    .append(" -> ").append(call.owner).append('.').append(call.name)
                                    .append(call.desc).append('\n');
                        }
                    }
                }
            }
            patch(source, overlay, "cn/judgfire/EnergyAttributes.class",
                    "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraftforge/registries/RegistryObject;Ljava/util/UUID;Z)D", audit);
            patch(source, overlay, "cn/judgfire/StrengthAttributes.class",
                    "(Lnet/minecraft/world/entity/LivingEntity;)D", audit);
        }
        Files.createDirectories(report.getParent());
        Files.writeString(report, audit);
        System.out.print(audit);
    }

    private static void patch(ZipFile source, Path overlay, String entry, String desc,
                              StringBuilder audit) throws Exception {
        ClassReader reader = new ClassReader(source.getInputStream(source.getEntry(entry)).readAllBytes());
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        List<MethodNode> targets = node.methods.stream()
                .filter(method -> method.name.equals("value") && method.desc.equals(desc)).toList();
        if (targets.size() != 1) throw new IllegalStateException("Expected exactly one value method: " + entry);
        MethodNode target = targets.get(0);
        List<MethodInsnNode> getters = new ArrayList<>();
        int instanceCalls = 0;
        for (AbstractInsnNode insn : target.instructions) {
            if (!(insn instanceof MethodInsnNode call)) continue;
            if (call.owner.equals(HELPER)) throw new IllegalStateException("Already guarded: " + entry);
            if (call.owner.equals(ENTITY) && call.desc.equals(ATTRIBUTES)) getters.add(call);
            if (GET_INSTANCE.contains(call.name) && call.desc.contains("ai/attributes/Attribute;")) instanceCalls++;
        }
        if (getters.size() != 1 || instanceCalls != 1)
            throw new IllegalStateException("Unexpected original attribute evaluator shape: " + entry);
        MethodInsnNode getter = getters.get(0);
        LabelNode original = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(ALOAD, 0));
        guard.add(new JumpInsnNode(IFNULL, original));
        guard.add(new VarInsnNode(ALOAD, 0));
        guard.add(new MethodInsnNode(getter.getOpcode(), getter.owner, getter.name, getter.desc, getter.itf));
        guard.add(new MethodInsnNode(INVOKESTATIC, HELPER, "ready", "(Ljava/lang/Object;)Z", false));
        guard.add(new JumpInsnNode(IFNE, original));
        guard.add(new InsnNode(DCONST_0));
        guard.add(new InsnNode(DRETURN));
        guard.add(original);
        guard.add(new FrameNode(F_SAME, 0, null, 0, null));
        target.instructions.insert(guard);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        Path output = overlay.resolve(entry);
        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
        audit.append("GUARDED ").append(node.name).append(".value").append(desc)
                .append("; original method instructions preserved after entry guard\n");
    }
}
