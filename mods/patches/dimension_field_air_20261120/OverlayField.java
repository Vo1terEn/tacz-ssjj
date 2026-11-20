import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Keep the other methods from the release, which includes later ASM patches. */
public final class OverlayField {
    private static final String ENTRY = "cn/ssjjdimension/DimensionField.class";
    private static final String PLAYER = "Lnet/minecraft/server/level/ServerPlayer;";
    private static final Map<String, String> REPLACEMENTS = Map.of(
        "cast", "(" + PLAYER + "I)V",
        "aim", "(" + PLAYER + "Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/phys/Vec3;"
    );

    public static void main(String[] args) throws Exception {
        var original = read(args[0]);
        var compiled = new ClassNode();
        read(args[1]).accept(compiled, 0);
        var methods = new HashMap<String, MethodNode>();
        for (var method : compiled.methods) {
            if (method.desc.equals(REPLACEMENTS.get(method.name))) methods.put(method.name, method);
        }
        if (!methods.keySet().equals(REPLACEMENTS.keySet())) {
            throw new IllegalStateException("Missing compiled field method");
        }
        var writer = new ClassWriter(original, 0);
        original.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                var target = super.visitMethod(access, name, desc, signature, exceptions);
                if (desc.equals(REPLACEMENTS.get(name))) {
                    var replacement = methods.remove(name);
                    if (replacement == null || replacement.access != access) {
                        throw new IllegalStateException("Incompatible field method " + name);
                    }
                    replacement.accept(target);
                    return null;
                }
                return target;
            }
        }, 0);
        if (!methods.isEmpty()) throw new IllegalStateException("Missing baseline field method");
        Files.write(Path.of(args[2]), writer.toByteArray());
    }

    private static ClassReader read(String path) throws Exception {
        try (var jar = new ZipFile(path); var file = jar.getInputStream(jar.getEntry(ENTRY))) {
            return new ClassReader(file);
        }
    }
}
