import java.nio.file.*;import java.util.*;import java.util.jar.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;

/** Compose narrowly scoped edits on the exact combat7 output, preserving stage changes. */
public class PatchFireBurst implements Opcodes {
 static final String HELPER="cn/judgfire/FireBurstRules";
 static byte[] read(JarFile jar,String name,Path extra)throws Exception {
  Path p=extra.resolve(name+".class");if(Files.exists(p))return Files.readAllBytes(p);
  return jar.getInputStream(jar.getJarEntry(name+".class")).readAllBytes();
 }
 public static void main(String[]args)throws Exception {
  Path out=Path.of(args[1]),extra=Path.of(args[2]);List<String> report=new ArrayList<>();
  int eligible=0,capture=0,crown=0,weapon=0,fallback=0;
  try(var jar=new JarFile(args[0])){for(String name:List.of("cn/judgfire/JudgmentFlame","cn/judgfire/BurstRuntime","cn/judgfire/AttackBonuses","cn/judgfire/EmperorCrown")) {
   byte[] original=read(jar,name,extra);var reader=new ClassReader(original);var n=new ClassNode();reader.accept(n,0);
   for(var m:n.methods){
    if(name.endsWith("/JudgmentFlame")&&m.name.equals("burst")&&m.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Z")) {
     m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
     m.instructions.add(new VarInsnNode(ALOAD,0));m.instructions.add(new MethodInsnNode(INVOKESTATIC,HELPER,"eligible",m.desc,false));m.instructions.add(new InsnNode(IRETURN));eligible++;
    }
    if(name.endsWith("/BurstRuntime")&&m.name.equals("tag"))for(var i:m.instructions.toArray())if(i.getOpcode()==RETURN){
     var add=new InsnList();add.add(new VarInsnNode(ALOAD,0));add.add(new VarInsnNode(ALOAD,1));
     add.add(new MethodInsnNode(INVOKESTATIC,HELPER,"capture",m.desc,false));m.instructions.insertBefore(i,add);capture++;
    }
    if(name.endsWith("/AttackBonuses")&&m.name.equals("gun"))for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode call){
     if(call.owner.equals("cn/judgfire/CrownAttack")&&call.name.equals("base")){
      m.instructions.insertBefore(call,new VarInsnNode(ALOAD,1));call.owner=HELPER;call.name="crownDamage";call.desc="(ZLcom/tacz/guns/entity/EntityKineticBullet;)F";crown++;
     }
     if(call.owner.equals("cn/judgfire/AttackBonuses")&&call.name.equals("weapon")){
      m.instructions.insertBefore(call,new VarInsnNode(ALOAD,1));call.owner=HELPER;call.desc="(Lnet/minecraft/world/entity/LivingEntity;FZLcom/tacz/guns/entity/EntityKineticBullet;)F";weapon++;
     }
    }
    if(name.endsWith("/EmperorCrown")&&m.name.equals("tick"))for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode call&&call.owner.equals("cn/judgfire/CrownAttack")&&call.name.equals("base")){
     m.instructions.insertBefore(call,new VarInsnNode(ALOAD,2));call.owner=HELPER;call.name="crownFallback";call.desc="(ZLnet/minecraft/world/item/ItemStack;)F";fallback++;
    }
   }
   var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);n.accept(writer);byte[] bytes=writer.toByteArray();var check=new ClassNode();new ClassReader(bytes).accept(check,0);
   for(var m:check.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name,m);
   var target=out.resolve(name+".class");Files.createDirectories(target.getParent());Files.write(target,bytes);
   report.add(name+" input="+(Files.exists(extra.resolve(name+".class"))?"combat7":"frozen6")+" methods stack verified="+n.methods.size());
  }
  }
  if(eligible!=1||capture!=1||crown!=1||weapon!=1||fallback!=1)throw new AssertionError("match counts "+eligible+"/"+capture+"/"+crown+"/"+weapon+"/"+fallback);
  report.add("PASS innate eligibility1 / firing snapshot1 / crown base1 / weapon base1 / fallback1");
  Files.write(out.getParent().resolve("patch_audit.txt"),report);System.out.println(String.join("\n",report));
 }
}
