import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
public final class PatchThunderBootstrap {
 public static void main(String[] args)throws Exception {
  byte[] original;
  try(JarFile jar=new JarFile(args[0])) {original=jar.getInputStream(jar.getJarEntry("cn/punishmenteye/EyeCrosshair.class")).readAllBytes();}
  ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] calls={0};
  reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
   @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
    MethodVisitor parent=super.visitMethod(access,name,descriptor,signature,exceptions);
    if(!name.equals("<init>"))return parent;
    return new MethodVisitor(Opcodes.ASM9,parent) {
     @Override public void visitInsn(int opcode) {
      if(opcode==Opcodes.RETURN) {super.visitMethodInsn(Opcodes.INVOKESTATIC,"cn/ssjjzeus/ZeusThunderNetwork","init","()V",false);calls[0]++;}
      super.visitInsn(opcode);
     }
    };
   }
  },0);
  if(calls[0]!=1)throw new AssertionError("Expected exactly one constructor return: "+calls[0]);
  Path destination=Path.of(args[1]);Files.createDirectories(destination.getParent());Files.write(destination,writer.toByteArray());
  System.out.println("PASS single early thunder network bootstrap injection");
 }
}
