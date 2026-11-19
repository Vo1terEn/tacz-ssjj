import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Insert one additive definition call; preserve every original method and option. */
public final class PatchDarkConfig {
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("input ArsenalConfig.class, output ArsenalConfig.class");
        var type=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(type,0);
        if(!type.name.equals("cn/fusionarsenal/config/ArsenalConfig"))throw new AssertionError("Wrong config class");
        for(var method:type.methods)for(var node:method.instructions.toArray())
            if(node instanceof MethodInsnNode call&&call.owner.equals("cn/ssjjdark/DarkCrystalConfig")&&call.name.equals("define"))
                throw new AssertionError("Dark Crystal config already registered");
        int hits=0;
        for(var method:type.methods)if(method.name.equals("<clinit>"))for(var node:method.instructions.toArray())
            if(node instanceof MethodInsnNode call&&call.owner.equals("java/util/Collections")&&call.name.equals("unmodifiableMap")){
                var insert=new InsnList();insert.add(new VarInsnNode(Opcodes.ALOAD,0));insert.add(new VarInsnNode(Opcodes.ALOAD,2));
                insert.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"cn/ssjjdark/DarkCrystalConfig","define","(Ljava/util/Map;Lnet/minecraftforge/common/ForgeConfigSpec$Builder;Ljava/util/Map;)Ljava/util/Map;",false));
                method.instructions.insertBefore(node,insert);hits++;break;
            }
        if(hits!=1)throw new AssertionError("Expected one config registration insertion: "+hits);
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);
        Path output=Path.of(args[1]);Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,writer.toByteArray());
        System.out.println("PASS additive Dark Crystal configuration registration");
    }
}
