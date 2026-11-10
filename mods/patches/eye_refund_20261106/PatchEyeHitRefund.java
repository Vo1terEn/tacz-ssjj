import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Replace only the result used by each existing refund, preserving its one native hurt call. */
public final class PatchEyeHitRefund implements Opcodes {
    public static void main(String[] args) throws Exception {
        ClassNode node = new ClassNode();
        try (JarFile archive = new JarFile(args[0])) {
            new ClassReader(archive.getInputStream(archive.getJarEntry("cn/punishmenteye/NofaPower.class")))
                .accept(node, ClassReader.EXPAND_FRAMES);
        }
        int lightning = 0, cloud = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("bullet") && !method.name.equals("tick")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                    || !call.owner.equals("net/minecraft/world/entity/LivingEntity")
                    || !call.name.equals("m_6469_")
                    || !call.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")) continue;
                AbstractInsnNode following = call.getNext();
                while (following != null && following.getOpcode() < 0) following = following.getNext();
                if (!(following instanceof MethodInsnNode refund)
                    || !refund.owner.equals("cn/punishmenteye/MuzzleRefund") || !refund.name.equals("hit"))
                    throw new IllegalStateException("Native hurt is not the existing refund expression");
                call.setOpcode(INVOKESTATIC);
                call.owner = "cn/punishmenteye/EyeHitRefund";
                call.name = "acceptedHit";
                call.desc = "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Z";
                call.itf = false;
                if (method.name.equals("bullet")) lightning++; else cloud++;
            }
        }
        if (lightning != 1 || cloud != 1)
            throw new IllegalStateException("Expected precisely one lightning and one cloud expression");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        Path output = Path.of(args[1]).resolve("cn/punishmenteye/NofaPower.class");
        Files.createDirectories(output.getParent()); Files.write(output, writer.toByteArray());
        System.out.println("PASS two exact hurt/refund expressions patched; other instructions retained");
    }
}
