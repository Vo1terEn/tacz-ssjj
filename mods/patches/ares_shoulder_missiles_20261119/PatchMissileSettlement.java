import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Route this server projectile through the existing verified direct-damage transaction. */
public final class PatchMissileSettlement implements Opcodes {
    private static final String DAMAGE = "cn/fusionarsenal/CreativeDamage";
    private static final String HEALTH = "cn/fusionarsenal/CreativeHealthState";
    private static final String MARKER = "cn/ssjjares/AresMissileDamage";
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String SOURCE = "Lnet/minecraft/world/damagesource/DamageSource;";
    private static final String APPLY = "(L" + LIVING + ";" + SOURCE + "FD)Z";

    public static void main(String[] args) throws Exception {
        try (var baseline = new ZipFile(args[0])) {
            patchDamage(baseline, Path.of(args[1]));
            patchHealth(baseline, Path.of(args[1]));
        }
    }

    private static void patchDamage(ZipFile baseline, Path output) throws Exception {
        var node = read(baseline, DAMAGE);
        var attack = method(node, "attack", "(L" + ENTITY + ";" + SOURCE + "F)Z");
        var normal = new LabelNode();
        var hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 1));
        hook.add(new TypeInsnNode(INSTANCEOF, MARKER));
        hook.add(new JumpInsnNode(IFEQ, normal));
        hook.add(new VarInsnNode(ALOAD, 0));
        hook.add(new TypeInsnNode(INSTANCEOF, LIVING));
        hook.add(new JumpInsnNode(IFEQ, normal));
        hook.add(new VarInsnNode(ALOAD, 0));
        hook.add(new TypeInsnNode(CHECKCAST, LIVING));
        hook.add(new VarInsnNode(ALOAD, 1));
        hook.add(new VarInsnNode(FLOAD, 2));
        hook.add(new InsnNode(DCONST_0));
        hook.add(new MethodInsnNode(INVOKESTATIC, DAMAGE, "apply", APPLY, false));
        hook.add(new InsnNode(IRETURN));
        hook.add(normal);
        hook.add(new FrameNode(F_SAME, 0, null, 0, null));
        attack.instructions.insert(hook);

        var apply = method(node, "apply", APPLY);
        var scale = matchedCall(apply, "cn/ssjjdark/DarkUltimateDamage", "scale", "(" + SOURCE + "F)F");
        scale.owner = MARKER;
        write(node, output);
    }

    private static void patchHealth(ZipFile baseline, Path output) throws Exception {
        var node = read(baseline, HEALTH);
        for (String name : List.of("ceiling", "request", "tick")) {
            var method = node.methods.stream().filter(candidate -> name.equals(candidate.name)).findFirst().orElseThrow();
            var enabled = matchedCall(method, HEALTH, "enabled", "()Z");
            var hook = new InsnList();
            hook.add(new VarInsnNode(ALOAD, name.equals("tick") ? 7 : 2));
            hook.add(new FieldInsnNode(GETFIELD, HEALTH + "$State", "source", SOURCE));
            hook.add(new MethodInsnNode(INVOKESTATIC, MARKER, "maintain", "(Z" + SOURCE + ")Z", false));
            method.instructions.insert(enabled, hook);
        }
        write(node, output);
    }

    private static MethodInsnNode matchedCall(MethodNode method, String owner, String name, String descriptor) {
        MethodInsnNode match = null;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && owner.equals(call.owner)
                && name.equals(call.name) && descriptor.equals(call.desc)) {
                if (match != null) throw new IllegalStateException("Multiple " + name + " calls in " + method.name);
                match = call;
            }
        }
        if (match == null) throw new IllegalStateException("Missing " + name + " call in " + method.name);
        return match;
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElseThrow();
    }

    private static ClassNode read(ZipFile source, String name) throws Exception {
        var node = new ClassNode();
        new ClassReader(source.getInputStream(source.getEntry(name + ".class"))).accept(node, 0);
        return node;
    }

    private static void write(ClassNode node, Path output) throws Exception {
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        var path = output.resolve(node.name + ".class");
        Files.createDirectories(path.getParent());
        Files.write(path, writer.toByteArray());
    }
}
