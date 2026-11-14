import com.google.gson.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Narrow overlays against the frozen actual installed native classes. */
public final class ZeusConfigPatches implements Opcodes {
 private static final String CONFIG="cn/fusionarsenal/config/ArsenalConfig";
 private static final String BONUS="cn/ssjjcompat/AttachmentBonusRuntime";
 private static final String SCALING="cn/judgfire/EnergyScaling";
 private static final String ITEM="Lnet/minecraft/world/item/ItemStack;";
 private static String sha(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
 private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
 private static String signature(MethodNode m){return m.name+m.desc;}
 private static String fingerprint(String owner,MethodNode method)throws Exception {
  ClassWriter w=new ClassWriter(0);w.visit(V17,ACC_PUBLIC,owner,null,"java/lang/Object",null);method.accept(w);w.visitEnd();return sha(w.toByteArray());
 }
 private static Map<String,String> methods(String owner,ClassNode c)throws Exception {
  var result=new TreeMap<String,String>();for(var m:c.methods)result.put(signature(m),fingerprint(owner,m));return result;
 }
 private static MethodNode method(ClassNode c,String name,String desc){return c.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
 private static AbstractInsnNode previous(AbstractInsnNode n){do{n=n.getPrevious();}while(n!=null&&(n.getType()==AbstractInsnNode.LABEL||n.getType()==AbstractInsnNode.LINE||n.getType()==AbstractInsnNode.FRAME));return n;}
 private static void config(ClassNode c) {
  var method=method(c,"<clinit>","()V");int sites=0;
  for(var n:method.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals("cn/ssjjdimension/DimensionConfig")&&call.name.equals("define")) {
   require(call.desc.equals("(Lnet/minecraftforge/common/ForgeConfigSpec$Builder;Ljava/util/Map;Ljava/util/Map;)V"),"Unexpected DimensionConfig bridge");
   var defaults=previous(n);var values=previous(defaults);var builder=previous(values);
   require(defaults instanceof VarInsnNode&&values instanceof VarInsnNode&&builder instanceof VarInsnNode,"Cannot locate spec local variables");
   require(defaults.getOpcode()==ALOAD&&values.getOpcode()==ALOAD&&builder.getOpcode()==ALOAD,"Spec bridge locals are not references");
   var add=new InsnList();for(var old:List.of(builder,values,defaults))add.add(new VarInsnNode(ALOAD,((VarInsnNode)old).var));
   add.add(new MethodInsnNode(INVOKESTATIC,"cn/ssjjzeus/ZeusConfig","define",call.desc,false));method.instructions.insert(n,add);sites++;
  }
  require(sites==1,"Expected exactly one spec bridge");method.maxStack=Math.max(method.maxStack,3);
 }
 private static void scaling(ClassNode c) {
  var method=method(c,"multiplier","(D)D");var keep=new LabelNode();var add=new InsnList();
  add.add(new VarInsnNode(DLOAD,0));add.add(new InsnNode(DCONST_0));add.add(new InsnNode(DCMPG));add.add(new JumpInsnNode(IFLT,keep));
  add.add(new LdcInsnNode("combat.penetration_damage_bonus"));add.add(new MethodInsnNode(INVOKESTATIC,CONFIG,"bool","(Ljava/lang/String;)Z",false));add.add(new JumpInsnNode(IFNE,keep));
  add.add(new InsnNode(DCONST_1));add.add(new InsnNode(DRETURN));add.add(keep);add.add(new FrameNode(F_SAME,0,null,0,null));
  method.instructions.insert(add);method.maxStack=Math.max(method.maxStack,4);
 }
 private static void bonus(ClassNode c) {
  var energy=method(c,"energy","(D"+ITEM+"Z)D");int energyReturns=0;
  for(var n:energy.instructions.toArray())if(n.getOpcode()==DRETURN){
   var add=new InsnList();add.add(new VarInsnNode(ALOAD,2));add.add(new VarInsnNode(ILOAD,3));
   add.add(new MethodInsnNode(INVOKESTATIC,"cn/ssjjcompat/ZeusAttachmentBonuses","energy",energy.desc,false));energy.instructions.insertBefore(n,add);energyReturns++;
  }
  require(energyReturns==1,"Unexpected energy exits");energy.maxStack=Math.max(energy.maxStack,4);
  var strength=method(c,"strength","(D"+ITEM+")D");int strengthReturns=0;
  for(var n:strength.instructions.toArray())if(n.getOpcode()==DRETURN){
   var add=new InsnList();add.add(new VarInsnNode(ALOAD,2));add.add(new MethodInsnNode(INVOKESTATIC,"cn/ssjjcompat/ZeusAttachmentBonuses","strength",strength.desc,false));strength.instructions.insertBefore(n,add);strengthReturns++;
  }
  require(strengthReturns==1,"Unexpected strength exits");strength.maxStack=Math.max(strength.maxStack,3);
  var cache=method(c,"cache","(Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;"+ITEM+")V");
  var add=new InsnList();add.add(new VarInsnNode(ALOAD,0));add.add(new VarInsnNode(ALOAD,1));add.add(new MethodInsnNode(INVOKESTATIC,"cn/ssjjzeus/ZeusNativeStats","cache",cache.desc,false));cache.instructions.insert(add);cache.maxStack=Math.max(cache.maxStack,2);
 }
 public static void main(String[] args)throws Exception {
  Path baseline=Path.of(args[0]),out=Path.of(args[1]),report=Path.of(args[2]);var result=new JsonObject();result.addProperty("passed",true);var classes=new JsonObject();
  try(var zip=new ZipFile(baseline.toFile())) {
   for(String owner:List.of(CONFIG,SCALING,BONUS)) {
    byte[] original=zip.getInputStream(zip.getEntry(owner+".class")).readAllBytes();var r=new ClassReader(original);var c=new ClassNode();r.accept(c,0);var before=methods(owner,c);
    switch(owner){case CONFIG->config(c);case SCALING->scaling(c);case BONUS->bonus(c);default->throw new AssertionError();}
    var writer=new ClassWriter(r,0);c.accept(writer);byte[] bytes=writer.toByteArray();var check=new ClassNode();new ClassReader(bytes).accept(check,0);var after=methods(owner,check);
    var changed=new ArrayList<String>();var unchanged=new ArrayList<String>();
    for(String key:before.keySet()){require(after.containsKey(key),"Removed method "+key);if(before.get(key).equals(after.get(key)))unchanged.add(key);else changed.add(key);}
    Set<String> expected=switch(owner){case CONFIG->Set.of("<clinit>()V");case SCALING->Set.of("multiplier(D)D");default->Set.of("energy(D"+ITEM+"Z)D","strength(D"+ITEM+")D","cache(Lcom/tacz/guns/resource/modifier/AttachmentCacheProperty;"+ITEM+")V");};
    require(Set.copyOf(changed).equals(expected),"Unexpected changed methods "+owner+changed);require(before.size()==after.size(),"Unexpected new methods");
    Path target=out.resolve(owner+".class");Files.createDirectories(target.getParent());Files.write(target,bytes);
    var info=new JsonObject();info.addProperty("old_sha256",sha(original));info.addProperty("new_sha256",sha(bytes));info.add("changed_methods",new Gson().toJsonTree(changed));info.add("unchanged_methods",new Gson().toJsonTree(unchanged));classes.add(owner+".class",info);
   }
  }
  result.add("classes",classes);Files.createDirectories(report.getParent());Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");System.out.println("PASS exact native config/attribute hooks; unrelated methods unchanged");
 }
}
