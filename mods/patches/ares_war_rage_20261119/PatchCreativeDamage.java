import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Preserve the existing Dark Crystal and Creative Stone wrappers on the production class. */
public final class PatchCreativeDamage {
    private static final String OWNER = "cn/fusionarsenal/CreativeDamage";
    private static final String ARGUMENTS = "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;";

    public static void main(String[] args) throws Exception {
        var node = new ClassNode();
        try (var baseline = new ZipFile(args[0])) {
            new ClassReader(baseline.getInputStream(baseline.getEntry(OWNER + ".class"))).accept(node, 0);
        }
        var original = node.methods.stream().filter(method -> method.name.equals("applyDarkUltimateOriginal")
            && method.desc.equals("(" + ARGUMENTS + "FD)Z")).findFirst().orElseThrow();
        int count = 0;
        for (var instruction : original.instructions.toArray()) {
            // Keep the final base/strength validation and clamp, with no new event dispatch.
            if (!(instruction instanceof MethodInsnNode call) || !OWNER.equals(call.owner)
                || !call.name.equals("amount") || !call.desc.equals("(FD)F")) continue;
            var hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.FSTORE, original.maxLocals));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
            hook.add(new VarInsnNode(Opcodes.FLOAD, original.maxLocals));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "cn/ssjjares/AresCombat", "scaleDirect",
                "(" + ARGUMENTS + "F)F", false));
            original.instructions.insert(call, hook);
            count++;
        }
        if (count != 1) throw new IllegalStateException("Unexpected Creative Stone settlement: " + count);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        var output = Path.of(args[1]);
        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
    }
}
