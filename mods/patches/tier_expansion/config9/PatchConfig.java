import java.nio.file.*;import java.util.*;import java.util.jar.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;
public final class PatchConfig implements Opcodes {
 static final String CFG="cn/fusionarsenal/config/ProgressionConfig";
 static void body(MethodNode m,InsnList b){m.instructions=b;m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();}
 static void write(ClassReader reader,ClassNode n,Path out)throws Exception{var w=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);n.accept(w);byte[] data=w.toByteArray();var check=new ClassNode();new ClassReader(data).accept(check,0);for(var m:check.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name,m);Path p=out.resolve(n.name+".class");Files.createDirectories(p.getParent());Files.write(p,data);}
 public static void main(String[]args)throws Exception{
  Path out=Path.of(args[1]);var audit=new ArrayList<String>();
  try(var jar=new JarFile(args[0])){
   for(String name:List.of("cn/fusionarsenal/FusionArsenal","cn/judgfire/EnergyAttributes","cn/punishmenteye/PassivePower","cn/punishmenteye/NofaPower","cn/punishmenteye/NofaNetwork$WeaponState","cn/punishmenteye/SkillTimer","cn/punishmenteye/NofaNetwork")){
    var reader=new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class")));var n=new ClassNode();reader.accept(n,0);int hits=0;
    for(var m:n.methods){
     if(name.endsWith("FusionArsenal")&&m.name.equals("<init>"))for(var ins:m.instructions.toArray())if(ins instanceof MethodInsnNode call&&call.name.equals("initialize")){
      m.instructions.insertBefore(ins,new MethodInsnNode(INVOKESTATIC,CFG,"register","()V",false));
      m.instructions.insertBefore(ins,new MethodInsnNode(INVOKESTATIC,"cn/judgfire/EffectPerformanceConfig","register","()V",false));
      hits++;audit.add(name+" constructor registers synced SERVER spec and sided CLIENT performance spec");
     }
     if(name.endsWith("EnergyAttributes")&&m.name.equals("amount"))for(var ins:m.instructions.toArray())if(ins instanceof MethodInsnNode call&&call.owner.equals("cn/fusionarsenal/EyeAccessoryEnergy")&&call.name.equals("resistanceOrPenetration")){
      m.instructions.insertBefore(ins,new VarInsnNode(ILOAD,1));call.desc="(Lnet/minecraft/world/item/ItemStack;Z)D";hits++;
     }
     if(name.endsWith("EnergyAttributes")&&m.name.equals("syncBurst"))for(var ins:m.instructions.toArray())if(ins instanceof LdcInsnNode v&&v.cst instanceof Double d&&d==500d){
      var previous=ins.getPrevious();while(previous!=null&&previous.getOpcode()<0)previous=previous.getPrevious();if(previous==null||previous.getOpcode()!=POP)throw new AssertionError("Expected native penetration bool before fixed500");m.instructions.remove(previous);
      m.instructions.set(ins,new MethodInsnNode(INVOKESTATIC,"cn/judgfire/CombatConfig","burstEnergy","(Z)D",false));hits++;
     }
     if(name.endsWith("PassivePower")&&m.name.equals("hasPower"))for(var ins:m.instructions.toArray())if(ins instanceof MethodInsnNode call&&call.owner.equals("cn/punishmenteye/EyeCombatRules")&&call.name.equals("isEye")){call.name="hasPassivePower";hits++;}
     if(name.endsWith("NofaPower"))for(var ins:m.instructions.toArray())if(ins instanceof LdcInsnNode v&&v.cst instanceof Float f&&((m.name.equals("bullet")&&f==10f)||(m.name.equals("tick")&&f==20f))){
      var b=new InsnList();b.add(new LdcInsnNode(f==10f?"eye.lightning_damage":"eye.cloud_damage"));b.add(new MethodInsnNode(INVOKESTATIC,CFG,"number","(Ljava/lang/String;)D",false));b.add(new InsnNode(D2F));m.instructions.insertBefore(ins,b);m.instructions.remove(ins);hits++;
     }
     if(name.endsWith("NofaNetwork$WeaponState")&&m.name.equals("valid")){
      var b=new InsnList();for(var field:List.of("duration","active","permanent")){b.add(new VarInsnNode(ALOAD,0));b.add(new FieldInsnNode(GETFIELD,name,field,field.equals("permanent")?"Z":"I"));}b.add(new MethodInsnNode(INVOKESTATIC,CFG,"validWeaponState","(IIZ)Z",false));b.add(new InsnNode(IRETURN));body(m,b);hits++;
     }
     if(name.endsWith("SkillTimer")||name.endsWith("NofaNetwork"))for(var ins:m.instructions.toArray())if(ins instanceof LdcInsnNode v&&v.cst instanceof String s&&s.equals(name.endsWith("SkillTimer")?"4":"6")){v.cst=name.endsWith("SkillTimer")?"5":"7";hits++;}
    }
    if(hits<1)throw new AssertionError("No config hooks in "+name);audit.add(name+": "+hits+" config hooks");write(reader,n,out);
   }
  }
  Files.write(out.getParent().resolve("patch_audit.txt"),audit);System.out.println(String.join("\n",audit));
 }
}
