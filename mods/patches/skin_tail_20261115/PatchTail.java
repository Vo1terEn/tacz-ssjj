import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class PatchTail {
 public static void main(String[] args)throws Exception {
  var n=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(n,0);int count=0;
  for(var m:n.methods)if(m.name.equals("installSkin")&&m.desc.contains("SkinRenderSink;")) {
   for(var i:m.instructions.toArray())if(i instanceof LdcInsnNode l&&"skin_fx_end".equals(l.cst)) {
    var before=i.getPrevious();if(!(before instanceof VarInsnNode v)||v.var!=0)throw new AssertionError("model load");
    var hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"cn/ssjjcompat/SkinTailOrder","ensure","(Lcom/tacz/guns/client/model/BedrockAnimatedModel;)V",false));
    m.instructions.insertBefore(before,hook);m.maxStack=Math.max(m.maxStack,1);count++;break;
   }
  }
  if(count!=1)throw new AssertionError("Expected one exact patch, got "+count);
  var out=new ClassWriter(0);n.accept(out);Files.write(Path.of(args[1]),out.toByteArray());
 }
}
