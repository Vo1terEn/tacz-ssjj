import java.nio.file.*;import java.util.*;import java.util.jar.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;
public class PatchGlobal implements Opcodes {
 static String CFG="cn/fusionarsenal/config/ArsenalConfig";
 static void call(InsnList b,String owner,String name,String desc){b.add(new MethodInsnNode(INVOKESTATIC,owner,name,desc,false));}
 static void field(InsnList b,String owner,String name,String desc){b.add(new FieldInsnNode(PUTSTATIC,owner,name,desc));}
 static void option(InsnList b,String key,String type){b.add(new LdcInsnNode(key));call(b,CFG,"option","(Ljava/lang/String;)Lnet/minecraftforge/common/ForgeConfigSpec$ConfigValue;");b.add(new TypeInsnNode(CHECKCAST,type));}
 static void replaceInit(ClassNode n,Map<String,String> map,String mapName,String mapClass){var m=n.methods.stream().filter(x->x.name.equals("<clinit>")).findFirst().orElseThrow();m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();var b=m.instructions;
  for(var f:n.fields){if((f.access&ACC_STATIC)==0||f.value!=null)continue;
   if(f.name.equals("CONFIG")){b.add(new FieldInsnNode(GETSTATIC,CFG,"SPEC","Lnet/minecraftforge/common/ForgeConfigSpec;"));}
   else if(f.name.equals(mapName)){b.add(new TypeInsnNode(NEW,mapClass));b.add(new InsnNode(DUP));b.add(new MethodInsnNode(INVOKESPECIAL,mapClass,"<init>","()V",false));}
   else {String key=map.get(f.name);if(key==null)throw new AssertionError("Unmapped initializer "+n.name+" "+f.name);option(b,key,Type.getType(f.desc).getInternalName());}
   field(b,n.name,f.name,f.desc);
  }b.add(new InsnNode(RETURN));
 }
 static void stripRegistration(MethodNode m){for(var ins:m.instructions.toArray())if(ins instanceof MethodInsnNode c&&c.owner.equals("net/minecraftforge/fml/ModLoadingContext")&&c.name.equals("registerConfig")){var b=new InsnList();for(int i=Type.getArgumentTypes(c.desc).length-1;i>=0;i--)b.add(new InsnNode(POP));b.add(new InsnNode(POP));m.instructions.insertBefore(ins,b);m.instructions.remove(ins);}}
 public static void main(String[]args)throws Exception{Path output=Path.of(args[1]);var summary=new ArrayList<String>();try(var jar=new JarFile(args[0])){
  for(String name:List.of("cn/fusionarsenal/FusionArsenal","cn/judgfire/JudgmentFlame","cn/punishmenteye/EyeCrosshair","cn/judgfire/RedLotus","cn/punishmenteye/PassivePower","cn/judgfire/BurstHud")){
   var reader=new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class")));var n=new ClassNode();reader.accept(n,0);
   if(name.endsWith("RedLotus"))replaceInit(n,Map.of("REDUCTION","fire.lotus_reduction_tier2","DURATION","fire.mark_duration_ticks","CROWN_DAMAGE","combat.crown_damage","CROWN_MID","fire.charge_mid_percent","CROWN_HIGH","fire.charge_high_percent","BURST_COOLDOWN","fire.burst_cooldown_ticks","BURST_STUN","fire.burst_stun_ticks","BURST_RESTUN","fire.burst_restun_ticks","BURST_IMMUNITY","fire.burst_immunity_ticks"),"LEVELS","java/util/WeakHashMap");
   else if(name.endsWith("PassivePower"))replaceInit(n,Map.of("MARK_SECONDS","eye.gaze_mark_seconds"),"LAST","java/util/HashMap");
   else for(var m:n.methods){
    if(name.endsWith("FusionArsenal")&&m.name.equals("<init>"))for(var ins:m.instructions.toArray())if(ins instanceof MethodInsnNode c&&c.name.equals("register")&&(c.owner.equals("cn/fusionarsenal/config/ProgressionConfig")||c.owner.equals("cn/judgfire/EffectPerformanceConfig"))){if(c.owner.endsWith("ProgressionConfig"))c.owner=CFG;else m.instructions.remove(ins);}
    if(name.endsWith("JudgmentFlame")||name.endsWith("EyeCrosshair"))stripRegistration(m);
    if(name.endsWith("BurstHud"))for(var ins:m.instructions.toArray())if(ins instanceof FieldInsnNode f&&f.getOpcode()==GETSTATIC&&f.owner.equals("cn/judgfire/RedLotus")&&f.name.equals("BURST_COOLDOWN")){var next=ins.getNext();while(next!=null&&!(next instanceof MethodInsnNode))next=next.getNext();if(!(next instanceof MethodInsnNode c)||!c.name.equals("get"))throw new AssertionError("Cooldown read");m.instructions.set(ins,new LdcInsnNode("fire.burst_cooldown_ticks"));m.instructions.set(c,new MethodInsnNode(INVOKESTATIC,CFG,"value","(Ljava/lang/String;)Ljava/lang/Object;",false));}
   }
   var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);n.accept(writer);byte[] bytes=writer.toByteArray();var check=new ClassNode();new ClassReader(bytes).accept(check,0);for(var m:check.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(name,m);Path p=output.resolve(name+".class");Files.createDirectories(p.getParent());Files.write(p,bytes);summary.add(name);
  }
 }Files.write(output.getParent().resolve("patch_audit.txt"),summary);System.out.println("PASS unified config registrations/initializers and client cooldown facade "+summary.size()+" classes");}
}
