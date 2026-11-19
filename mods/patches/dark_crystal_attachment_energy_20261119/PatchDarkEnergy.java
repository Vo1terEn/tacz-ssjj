import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Feed both native held-item paths while preserving all existing weapon contributions. */
public final class PatchDarkEnergy implements Opcodes {
    public static void main(String[] args) throws Exception {
        var owner = "cn/fusionarsenal/EyeAccessoryEnergy";
        var item = "Lnet/minecraft/world/item/ItemStack;";
        var node = new ClassNode();
        try (var jar = new ZipFile(args[0])) {
            new ClassReader(jar.getInputStream(jar.getEntry(owner + ".class"))).accept(node, 0);
        }
        for (var entry : Map.of("resistanceOrPenetration", "(" + item + "Z)D", "strength", "(" + item + ")D").entrySet()) {
            var method = node.methods.stream()
                .filter(m -> m.name.equals(entry.getKey()) && m.desc.equals(entry.getValue())).findFirst().orElseThrow();
            var returns = java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() == DRETURN).toList();
            if (returns.size() != 1) throw new IllegalStateException("Unexpected energy return: " + method.name);
            var hook = new InsnList();
            hook.add(new VarInsnNode(ALOAD, 0));
            hook.add(new MethodInsnNode(INVOKESTATIC, "cn/ssjjdark/DarkAccessoryEnergy", "bonus", "(" + item + ")D", false));
            hook.add(new InsnNode(DADD));
            method.instructions.insertBefore(returns.get(0), hook);
        }
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        var output = Path.of(args[1]);
        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
    }
}
