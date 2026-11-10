import java.nio.file.*;import java.util.*;import java.util.jar.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;
public final class PatchSkillAction implements Opcodes {
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[1]);int calls=0,returns=0;
  try(var jar=new JarFile(args[0])){for(String name:List.of("cn/punishmenteye/SkillInput","cn/punishmenteye/SkillVisualState")){
   var cr=new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class")));var node=new ClassNode();cr.accept(node,0);
   for(var method:node.methods){
    if(name.endsWith("SkillInput")&&method.name.equals("tick"))for(var ins:method.instructions.toArray())if(ins instanceof MethodInsnNode call&&call.owner.equals("cn/punishmenteye/SkillTimer")&&call.name.equals("request")){method.instructions.insertBefore(ins,new MethodInsnNode(INVOKESTATIC,"cn/punishmenteye/SkillActionClient","requested","()V",false));calls++;}
    if(name.endsWith("SkillVisualState")&&method.name.equals("accept"))for(var ins:method.instructions.toArray())if(ins.getOpcode()==RETURN){var code=new InsnList();code.add(new VarInsnNode(ALOAD,0));code.add(new MethodInsnNode(INVOKESTATIC,"cn/punishmenteye/SkillActionClient","accept","(Lcn/punishmenteye/NofaNetwork$WeaponState;)V",false));method.instructions.insertBefore(ins,code);returns++;}
   }
   var writer=new ClassWriter(cr,ClassWriter.COMPUTE_MAXS);node.accept(writer);var bytes=writer.toByteArray();var check=new ClassNode();new ClassReader(bytes).accept(check,0);for(var m:check.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name,m);
   var dst=out.resolve(name+".class");Files.createDirectories(dst.getParent());Files.write(dst,bytes);
  }}
  if(calls!=1||returns!=2)throw new AssertionError("unexpected skill hook sites "+calls+" / "+returns);
  System.out.println("PASS one native V-request call and two validated/early-return state endpoints hooked; all pre-existing method bodies retained");
 }
}
