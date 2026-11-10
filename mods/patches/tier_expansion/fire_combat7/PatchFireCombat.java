import java.nio.file.*;import java.util.*;import java.util.jar.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;
/** Patches exact production bodies and can compose on a jar containing other current changes. */
public class PatchFireCombat implements Opcodes {
 static final String HELPER="cn/judgfire/FireCombatStages",STACK="Lnet/minecraft/world/item/ItemStack;",LIVING="Lnet/minecraft/world/entity/LivingEntity;",BULLET="Lcom/tacz/guns/entity/EntityKineticBullet;";
 static int crown,lotus,range,reduce,gun,auto,enabled;
 static void require(boolean c,String s){if(!c)throw new AssertionError(s);}
 static void replace(MethodNode m,String name){m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();m.instructions.add(new VarInsnNode(ALOAD,0));m.instructions.add(new MethodInsnNode(INVOKESTATIC,HELPER,name,"("+STACK+")Z",false));m.instructions.add(new InsnNode(IRETURN));}
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[1]);try(var jar=new JarFile(args[0])){
   for(String name:List.of("JudgmentFlame","CrownTargets","RedLotus","AttackBonuses","EmperorCrown")){
    ClassReader reader=new ClassReader(jar.getInputStream(jar.getJarEntry("cn/judgfire/"+name+".class")));var n=new ClassNode();reader.accept(n,0);
    for(var m:n.methods){
     if(name.equals("JudgmentFlame")&&m.name.equals("crown")){replace(m,"crown");crown++;}
     if(name.equals("JudgmentFlame")&&m.name.equals("lotus")){replace(m,"lotus");lotus++;}
     if(name.equals("CrownTargets")&&m.name.equals("collect"))for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals("cn/judgfire/CrownCharge")&&c.name.equals("inRange")){
      m.instructions.insertBefore(c,new VarInsnNode(ALOAD,1));c.owner=HELPER;c.desc="(DDD"+LIVING+")Z";range++;
     }
     if(name.equals("RedLotus")&&m.name.equals("reduce"))for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals("cn/judgfire/CombatConfig")&&c.name.equals("reduction")){
      var a=new InsnList();a.add(new VarInsnNode(ALOAD,1));a.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraft/world/entity/LivingEntity","m_21205_","()"+STACK,false));m.instructions.insertBefore(c,a);c.owner=HELPER;c.desc="("+STACK+")D";reduce++;
     }
     if(name.equals("AttackBonuses")&&m.name.equals("gun"))for(var i:m.instructions.toArray())if(i.getOpcode()==FRETURN){
      var a=new InsnList();a.add(new VarInsnNode(ALOAD,1));a.add(new MethodInsnNode(INVOKESTATIC,HELPER,"gunDamage","(F"+BULLET+")F",false));m.instructions.insertBefore(i,a);gun++;
     }
     if(name.equals("EmperorCrown")&&m.name.equals("tick"))for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c){
      if(c.owner.equals("cn/judgfire/CrownRangeBonus")&&c.name.equals("apply")){
       var a=new InsnList();a.add(new VarInsnNode(ALOAD,2));a.add(new MethodInsnNode(INVOKESTATIC,HELPER,"crownDamage","(F"+STACK+")F",false));m.instructions.insert(c,a);auto++;
      }
      if(c.owner.equals("cn/judgfire/CombatConfig")&&c.name.equals("range")){
       m.instructions.insertBefore(c,new VarInsnNode(ALOAD,2));c.owner=HELPER;c.desc="("+STACK+")D";enabled++;
      }
     }
    }
    var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);n.accept(writer);byte[] bytes=writer.toByteArray();var check=new ClassNode();new ClassReader(bytes).accept(check,0);for(var m:check.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(n.name,m);
    Path p=out.resolve(n.name+".class");Files.createDirectories(p.getParent());Files.write(p,bytes);
   }
  }
  require(crown==1&&lotus==1&&range==1&&reduce==1&&gun==1&&auto==1&&enabled==1,"Unexpected production hooks "+List.of(crown,lotus,range,reduce,gun,auto,enabled));
  System.out.println("PASS innate Crown/Lotus, loaded-target boundary, stage reduction, gun stage damage and single Crown stage multiplier: "+List.of(crown,lotus,range,reduce,gun,auto,enabled));
 }
}
