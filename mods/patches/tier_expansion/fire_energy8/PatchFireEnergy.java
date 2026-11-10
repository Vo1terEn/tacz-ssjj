import java.nio.file.*;import java.util.*;import java.util.jar.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;

/** Preserve all production bodies outside the active-energy allowance and its configured amount. */
public final class PatchFireEnergy implements Opcodes {
 static final String HELPER="cn/judgfire/FireBurstEnergy";
 static void write(ClassReader reader,ClassNode node,Path out)throws Exception{
  var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);node.accept(writer);byte[] bytes=writer.toByteArray();
  var verify=new ClassNode();new ClassReader(bytes).accept(verify,0);
  for(var m:verify.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(verify.name,m);
  Path path=out.resolve(node.name+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);
 }
 public static void main(String[]args)throws Exception{
  Path out=Path.of(args[1]);int runtime=0,sync=0,amount=0;
  try(var jar=new JarFile(args[0])){
   for(String name:List.of("cn/judgfire/BurstRuntime","cn/judgfire/EnergyAttributes")){
    var reader=new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class")));var node=new ClassNode();reader.accept(node,0);
    for(var m:node.methods){
     if(name.endsWith("BurstRuntime")&&m.name.equals("energyBonus")&&m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;Z)D")){
      m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
      m.instructions.add(new VarInsnNode(ALOAD,0));m.instructions.add(new VarInsnNode(ILOAD,1));
      m.instructions.add(new MethodInsnNode(INVOKESTATIC,HELPER,"bonus",m.desc,false));m.instructions.add(new InsnNode(DRETURN));runtime++;
     }
     if(name.endsWith("EnergyAttributes")&&m.name.equals("syncBurst")&&m.desc.equals("(Lnet/minecraft/world/entity/player/Player;Z)V")){
      var prefix=new InsnList();prefix.add(new VarInsnNode(ALOAD,0));prefix.add(new VarInsnNode(ILOAD,1));
      prefix.add(new MethodInsnNode(INVOKESTATIC,HELPER,"allowed","(Lnet/minecraft/world/entity/player/Player;Z)Z",false));prefix.add(new VarInsnNode(ISTORE,1));m.instructions.insert(prefix);sync++;
     }
     if(name.endsWith("EnergyAttributes")&&m.name.equals("syncBurst")&&m.desc.equals("(Lnet/minecraft/world/entity/player/Player;Lnet/minecraftforge/registries/RegistryObject;Ljava/util/UUID;Z)V")){
      for(var ins:m.instructions.toArray())if(ins instanceof MethodInsnNode call&&call.owner.equals("cn/judgfire/CombatConfig")&&call.name.equals("burstEnergy")&&call.desc.equals("(Z)D")){
       var replacement=new InsnList();replacement.add(new InsnNode(POP));replacement.add(new LdcInsnNode(500d));m.instructions.insertBefore(ins,replacement);m.instructions.remove(ins);amount++;
      }
     }
    }
    write(reader,node,out);
   }
  }
  if(runtime!=1||sync!=1||amount!=1)throw new AssertionError("Unexpected production matches "+runtime+"/"+sync+"/"+amount);
  Files.writeString(out.getParent().resolve("patch_audit.txt"),"BurstRuntime energyBonus delegate=1\nEnergyAttributes public live-gate=1\nEnergyAttributes private amount fixed500=1\nOther method bodies retained\n");
  System.out.println("PASS live/server modifier energy gate; fixed500 each, original damage/activation methods retained");
 }
}
