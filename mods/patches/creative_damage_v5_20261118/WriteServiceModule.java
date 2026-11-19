import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

/** Writes the parent SERVICE view's descriptor without compiling GAME sources as a module. */
public final class WriteServiceModule {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output module-info.class path");
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor module = writer.visitModule("ssjjrpg.health.bootstrap", 0, null);
        module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
        for (String dependency : new String[]{"cpw.mods.modlauncher", "cpw.mods.securejarhandler",
                "org.objectweb.asm", "org.objectweb.asm.tree", "net.minecraftforge.forgespi", "fmlloader"})
            module.visitRequire(dependency, 0, null);
        module.visitPackage("cn/fusionarsenal/launch");
        module.visitExport("cn/fusionarsenal/launch", 0);
        module.visitProvide("cpw/mods/modlauncher/api/ITransformationService",
            "cn/fusionarsenal/launch/HealthTransformationService");
        module.visitProvide("net/minecraftforge/forgespi/locating/IModLocator",
            "cn/fusionarsenal/launch/SelfModLocator");
        module.visitEnd();
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        ModuleDescriptor descriptor = ModuleDescriptor.read(java.nio.ByteBuffer.wrap(bytes));
        if (descriptor.isAutomatic() || !descriptor.exports().stream().map(ModuleDescriptor.Exports::source)
                .collect(java.util.stream.Collectors.toSet()).equals(Set.of("cn.fusionarsenal.launch")))
            throw new AssertionError("SERVICE descriptor must export only launch");
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, bytes);
        System.out.println("PASS explicit SERVICE module " + descriptor.name() + ", two providers, only launch exported");
    }
}
