import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class PatchAresConfig {
 public static void main(String[] args)throws Exception{
  var c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(c,0);int hits=0;
  for(var m:c.methods)if(m.name.equals("<clinit>"))for(var n:m.instructions.toArray())
   if(n instanceof MethodInsnNode call&&call.owner.equals("java/util/Collections")&&call.name.equals("unmodifiableMap")){
    var insert=new InsnList();insert.add(new VarInsnNode(Opcodes.ALOAD,0));insert.add(new VarInsnNode(Opcodes.ALOAD,2));
    insert.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"cn/ssjjares/ysm/AresAppearanceConfig","define","(Ljava/util/Map;Lnet/minecraftforge/common/ForgeConfigSpec$Builder;Ljava/util/Map;)Ljava/util/Map;",false));
    m.instructions.insertBefore(n,insert);hits++;break;
   }
  if(hits!=1)throw new AssertionError("Expected one config registration insertion: "+hits);
  var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.createDirectories(Path.of(args[1]).getParent());Files.write(Path.of(args[1]),w.toByteArray());
  System.out.println("PASS additive config registration; existing config bytecode preserved");
 }
}
