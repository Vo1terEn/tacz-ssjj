import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Preserve the exact installed health/stun implementation and add two return-value hooks. */
public final class PatchLiberationHooks implements Opcodes {
 static byte[] harden(byte[] bytes,String kind,Map<String,Object> report){
  ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);int count=0;
  String entity="Lnet/minecraft/world/entity/LivingEntity;";
  String target=kind.equals("creative_attack")?"apply":kind.equals("bridge")?"write":"commit";
  String descriptor=kind.equals("creative_attack")?"("+entity+"Lnet/minecraft/world/damagesource/DamageSource;FD)Z":
    kind.equals("deep_commit")?"("+entity+"F)Lcn/fusionarsenal/health/DeepHealthProbe$Commit;":"("+entity+"F)V";
  for(MethodNode m:c.methods)if(m.name.equals(target)&&m.desc.equals(descriptor)){
   for(AbstractInsnNode ins:m.instructions.toArray())if(ins instanceof MethodInsnNode call&&call.owner.equals("cn/ssjjdark/DarkLiberationGuard"))throw new IllegalStateException("Guard already present");
   InsnList code=new InsnList();
   code.add(new VarInsnNode(ALOAD,0));
   if(kind.equals("creative_attack")){
    code.add(new MethodInsnNode(INVOKESTATIC,"cn/ssjjdark/DarkLiberationGuard","blocked","("+entity+")Z",false));
    LabelNode proceed=new LabelNode();code.add(new JumpInsnNode(IFEQ,proceed));
    // True means handled: do not fall through to any alternate native damage route.
    code.add(new InsnNode(ICONST_1));code.add(new InsnNode(IRETURN));
    code.add(proceed);code.add(new FrameNode(F_SAME,0,null,0,null));
   }else{
    code.add(new VarInsnNode(FLOAD,1));
    code.add(new MethodInsnNode(INVOKESTATIC,"cn/ssjjdark/DarkLiberationGuard","health","("+entity+"F)F",false));
    code.add(new VarInsnNode(FSTORE,1));
   }
   m.instructions.insert(code);count++;
  }
  if(count!=1)throw new IllegalStateException("Expected exactly one hardening target: "+kind+" "+count);
  report.put(kind+"_entry_guard",true);
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);return writer.toByteArray();
 }
 static byte[] patch(byte[] bytes,String kind,Map<String,Object> report){
  ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);
  int count=0;
  String entity="Lnet/minecraft/world/entity/LivingEntity;";
  String descriptor=kind.equals("health")?"("+entity+"FFZ)F":"("+entity+")Z";
  String target=kind.equals("health")?"adjust":"stunned";
  for(MethodNode m:c.methods)if(m.name.equals(target)&&m.desc.equals(descriptor)){
   int injected=0;
   for(AbstractInsnNode ins:m.instructions.toArray()){
    if(ins instanceof MethodInsnNode call&&call.owner.equals("cn/ssjjdark/DarkLiberation"))
     throw new IllegalStateException("Hook already present");
    if(ins.getOpcode()!=(kind.equals("health")?FRETURN:IRETURN))continue;
    InsnList code=new InsnList();
    if(kind.equals("health")){
     int result=m.maxLocals;
     code.add(new VarInsnNode(FSTORE,result));
     code.add(new VarInsnNode(ALOAD,0));
     code.add(new VarInsnNode(FLOAD,1));
     code.add(new VarInsnNode(FLOAD,result));
     code.add(new VarInsnNode(ALOAD,0));
     code.add(new MethodInsnNode(INVOKESTATIC,c.name,"restoring","("+entity+")Z",false));
     code.add(new MethodInsnNode(INVOKESTATIC,"cn/ssjjdark/DarkLiberation","healthBoundary",descriptor,false));
    }else{
     code.add(new VarInsnNode(ALOAD,0));
     code.add(new MethodInsnNode(INVOKESTATIC,"cn/ssjjdark/DarkLiberation","charging",descriptor,false));
     code.add(new InsnNode(IOR));
    }
    m.instructions.insertBefore(ins,code);injected++;
   }
   if(injected==0)throw new IllegalStateException("No target returns");
   if(kind.equals("health"))m.maxLocals++;
   report.put(kind+"_returns_hooked",injected);count++;
  }
  if(count!=1)throw new IllegalStateException("Expected exactly one target method: "+kind+" "+count);
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);return writer.toByteArray();
 }
 public static void main(String[]args)throws Exception{
  Path runtime=Path.of(args[1]);Map<String,byte[]> entries=new LinkedHashMap<>();
  try(JarFile input=new JarFile(runtime.toFile())){
   for(var e:Collections.list(input.entries()))if(!e.isDirectory())entries.put(e.getName(),input.getInputStream(e).readAllBytes());
  }
  Map<String,Object> report=new LinkedHashMap<>();
  try(JarFile baseline=new JarFile(args[0])){
   for(var p:Map.of("cn/judgfire/BarrierHealth.class","health","cn/judgfire/BurstStun.class","stun").entrySet()){
    if(entries.containsKey(p.getKey()))throw new IllegalStateException("Unexpected source override "+p.getKey());
    entries.put(p.getKey(),patch(baseline.getInputStream(baseline.getJarEntry(p.getKey())).readAllBytes(),p.getValue(),report));
   }
   for(var p:Map.of("cn/fusionarsenal/CreativeDamage.class","creative_attack",
       "cn/fusionarsenal/CreativeHealthState.class","creative_state",
       "cn/fusionarsenal/health/DeepHealthProbe.class","deep_commit",
       "cn/judgfire/CreativeHealthBridge.class","bridge").entrySet()){
    if(entries.containsKey(p.getKey()))throw new IllegalStateException("Unexpected source override "+p.getKey());
    entries.put(p.getKey(),harden(baseline.getInputStream(baseline.getJarEntry(p.getKey())).readAllBytes(),p.getValue(),report));
   }
  }
  Path output=runtime.resolveSibling(runtime.getFileName()+".hooks");
  try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(output))){
   for(var e:entries.entrySet()){out.putNextEntry(new JarEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}
  }
  Files.move(output,runtime,StandardCopyOption.REPLACE_EXISTING);
  report.put("passed",true);report.put("scope","Two return hooks and four dedicated health-entry guards; original installed method bodies retained");
  report.put("live_game_test",false);FreshRemap.report(Path.of(args[2]),report);
 }
}
