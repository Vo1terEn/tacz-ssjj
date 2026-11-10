import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Precise additive calls; every original instruction, method and frame remains. */
public final class AttachmentBonusPatch {
 static final String R="cn/ssjjcompat/AttachmentBonusRuntime",STACK="Lnet/minecraft/world/item/ItemStack;",TAG="Lnet/minecraft/nbt/CompoundTag;",ID="Lnet/minecraft/resources/ResourceLocation;";
 static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
 static boolean call(AbstractInsnNode n,String owner,String name){return n instanceof MethodInsnNode m&&m.owner.equals(owner)&&m.name.equals(name);}
 static ClassNode read(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
 static byte[] write(ClassNode n){var w=new ClassWriter(0);n.accept(w);return w.toByteArray();}
 static byte[] patch(byte[] original,String target,Map<String,Object> report)throws Exception{
  var node=new ClassNode();new ClassReader(original).accept(node,0);int existing=0;
  for(var m:node.methods)for(var n:m.instructions)if(n instanceof MethodInsnNode c&&c.owner.equals(R))existing++;
  int expected=switch(target){case "cn/fusionarsenal/EyeAccessoryEnergy"->2;case "cn/ssjjparasite2/client/ParasiteCannon"->3;case "cn/punishmenteye/NofaPower"->4;default->throw new IllegalArgumentException(target);};
  if(existing>0){require(existing==expected,"Partial prior patch "+target+" "+existing);return original;}
  int changes=0;
  for(var m:node.methods){var insns=m.instructions.toArray();
   if(target.endsWith("EyeAccessoryEnergy")){
    String bridge=m.name.equals("resistanceOrPenetration")&&m.desc.equals("("+STACK+"Z)D")?"energy":m.name.equals("strength")&&m.desc.equals("("+STACK+")D")?"strength":null;
    if(bridge==null)continue;
    for(var n:insns)if(n.getOpcode()==Opcodes.DRETURN){var p=new InsnList();p.add(new VarInsnNode(Opcodes.ALOAD,0));if(bridge.equals("energy"))p.add(new VarInsnNode(Opcodes.ILOAD,1));p.add(new MethodInsnNode(Opcodes.INVOKESTATIC,R,bridge,"(D"+STACK+(bridge.equals("energy")?"Z":"")+")D",false));m.instructions.insertBefore(n,p);changes++;}m.maxStack+=2;
   }else if(target.endsWith("ParasiteCannon")&&m.name.equals("damage")&&m.desc.equals("(F"+ID+TAG+")F")){
    for(var n:insns)if(n.getOpcode()==Opcodes.FRETURN){var p=new InsnList();p.add(new VarInsnNode(Opcodes.ALOAD,1));p.add(new VarInsnNode(Opcodes.ALOAD,2));p.add(new MethodInsnNode(Opcodes.INVOKESTATIC,R,"parasiteDamage","(F"+ID+TAG+")F",false));m.instructions.insertBefore(n,p);changes++;}m.maxStack+=2;
   }else if(target.endsWith("NofaPower")){
    for(var n:insns){
     if(n instanceof MethodInsnNode c&&c.owner.equals("cn/fusionarsenal/config/ProgressionConfig")&&c.name.equals("number")&&c.desc.equals("(Ljava/lang/String;)D")){
      var prev=n.getPrevious();if(!(prev instanceof LdcInsnNode ldc))continue;String key=String.valueOf(ldc.cst);if(!Set.of("eye.lightning_damage","eye.cloud_damage").contains(key))continue;
      boolean lightning=key.equals("eye.lightning_damage");require(lightning?m.name.equals("bullet"):m.name.equals("tick"),"Nofa damage owner changed");var p=new InsnList();p.add(new VarInsnNode(Opcodes.ALOAD,lightning?3:2));p.add(new MethodInsnNode(Opcodes.INVOKESTATIC,R,lightning?"eyeDamage":"eyeCloudDamage",lightning?"(D"+STACK+")D":"(DLjava/lang/Object;)D",false));m.instructions.insert(n,p);changes++;
     }
     if(n instanceof MethodInsnNode c&&c.owner.equals("java/util/List")&&c.name.equals("add")&&c.desc.equals("(Ljava/lang/Object;)Z")){
      var a=n.getPrevious();var b=a==null?null:a.getPrevious();if(!(a instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD)||!(b instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&f.owner.equals(target)))continue;
      String bridge=f.name.equals("BALLS")&&m.name.equals("bullet")?"captureEyeSkill":f.name.equals("CLOUDS")&&m.name.equals("cloud")?"inheritEyeSkill":null;if(bridge==null)continue;
      var p=new InsnList();p.add(new InsnNode(Opcodes.DUP));p.add(new VarInsnNode(Opcodes.ALOAD,bridge.equals("captureEyeSkill")?3:0));p.add(new MethodInsnNode(Opcodes.INVOKESTATIC,R,bridge,bridge.equals("captureEyeSkill")?"(Ljava/lang/Object;"+STACK+")V":"(Ljava/lang/Object;Ljava/lang/Object;)V",false));m.instructions.insertBefore(n,p);changes++;
     }
    }m.maxStack+=2;
   }
  }
  require(changes==expected,"Expected exact attachment hooks "+target+" "+changes+"/"+expected);
  for(var m:node.methods)if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,m);
  byte[] out=write(node);var stripped=read(out);var before=read(original);
  for(int i=0;i<stripped.methods.size();i++){var m=stripped.methods.get(i);for(var n:m.instructions.toArray())if(n instanceof MethodInsnNode c&&c.owner.equals(R)){
   int args=Set.of("energy","parasiteDamage","captureEyeSkill","inheritEyeSkill").contains(c.name)?2:1;
   for(int j=0;j<args;j++)m.instructions.remove(n.getPrevious());m.instructions.remove(n);
  }m.maxStack=before.methods.get(i).maxStack;}
  require(Arrays.equals(write(before),write(stripped)),"Original instruction/frame/attribute bytes differ after removing additions: "+target);
  report.put(target,Map.of("hooks",changes,"all_original_instructions_preserved",true,"frames_preserved",true,"ASM_basic_verifier",true,"byte_idempotent",Arrays.equals(out,patch(out,target,new HashMap<>()))));return out;
 }
 public static void main(String[] args)throws Exception{
  var root=Path.of(args[0]);var out=Path.of(args[1]);var report=new LinkedHashMap<String,Object>();
  for(String name:List.of("cn/fusionarsenal/EyeAccessoryEnergy","cn/ssjjparasite2/client/ParasiteCannon","cn/punishmenteye/NofaPower")){var p=out.resolve(name+".class");Files.createDirectories(p.getParent());Files.write(p,patch(Files.readAllBytes(root.resolve(name+".class")),name,report));}
  report.put("passed",true);Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report));
 }
}
