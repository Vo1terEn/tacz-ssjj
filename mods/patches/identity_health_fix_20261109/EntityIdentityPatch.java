import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Entity.equals is network-id equality, not logical-side/object identity. State keys must be identity keys. */
public final class EntityIdentityPatch {
 static final Set<String> ENTITIES=Set.of("net/minecraft/world/entity/Entity","net/minecraft/world/entity/LivingEntity","net/minecraft/world/entity/player/Player","net/minecraft/server/level/ServerPlayer","com/tacz/guns/entity/EntityKineticBullet","net/minecraft/world/entity/projectile/AbstractArrow","net/minecraft/world/entity/projectile/ThrownTrident");
 static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static AbstractInsnNode previous(AbstractInsnNode n){do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
 public static void main(String[]args)throws Exception{
  var results=new LinkedHashMap<String,Object>();int total=0;
  try(var jar=new JarFile(args[0])){
   for(var entry:Collections.list(jar.entries())){
    if(!entry.getName().endsWith(".class")||!(entry.getName().startsWith("cn/")||entry.getName().startsWith("ssjj/")))continue;
    byte[] bytes;try(var in=jar.getInputStream(entry)){bytes=in.readAllBytes();}
    var node=new ClassNode();new ClassReader(bytes).accept(node,0);var targets=new HashSet<String>();
    for(var f:node.fields)if(f.signature!=null&&(f.access&Opcodes.ACC_STATIC)!=0&&f.desc.equals("Ljava/util/Map;"))
     for(var entity:ENTITIES)if(f.signature.startsWith("Ljava/util/Map<L"+entity+";"))targets.add(f.name);
    if(targets.isEmpty())continue;var changed=new ArrayList<String>();
    for(var method:node.methods)for(var n:method.instructions.toArray()){
     if(!(n instanceof FieldInsnNode field)||n.getOpcode()!=Opcodes.PUTSTATIC||!field.owner.equals(node.name)||!targets.contains(field.name))continue;
     var last=previous(n);boolean synchronizedMap=last instanceof MethodInsnNode m&&m.owner.equals("java/util/Collections")&&m.name.equals("synchronizedMap");
     var init=synchronizedMap?previous(last):last;
     if(!(init instanceof MethodInsnNode m)||!m.owner.equals("java/util/WeakHashMap")||!m.name.equals("<init>"))continue;
     var dup=previous(init);var create=previous(dup);
     require(dup.getOpcode()==Opcodes.DUP&&create instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW&&t.desc.equals("java/util/WeakHashMap"),"Unsupported weak entity map init: "+node.name+'.'+field.name);
     var patch=new InsnList();patch.add(new TypeInsnNode(Opcodes.NEW,"com/google/common/collect/MapMaker"));patch.add(new InsnNode(Opcodes.DUP));
     patch.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"com/google/common/collect/MapMaker","<init>","()V",false));
     patch.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"com/google/common/collect/MapMaker","weakKeys","()Lcom/google/common/collect/MapMaker;",false));
     patch.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"com/google/common/collect/MapMaker","makeMap","()Ljava/util/concurrent/ConcurrentMap;",false));
     method.instructions.insertBefore(create,patch);method.instructions.remove(create);method.instructions.remove(dup);method.instructions.remove(init);if(synchronizedMap)method.instructions.remove(last);
     changed.add(field.name);total++;
    }
    if(changed.isEmpty())continue;
    for(var method:node.methods)if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
    var writer=new ClassWriter(0);node.accept(writer);var output=Path.of(args[1]).resolve(entry.getName());Files.createDirectories(output.getParent());Files.write(output,writer.toByteArray());results.put(node.name,changed);
   }
  }
  require(results.containsKey("cn/judgfire/BarrierHealth"),"Missing health fix");
  var report=new LinkedHashMap<String,Object>();report.put("passed",true);report.put("entity_maps_changed",total);report.put("fields",results);report.put("key_semantics","Guava weakKeys uses object identity, with weak references and concurrent access");
  Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println("PASS identity patch "+results.size()+" classes / "+total+" entity maps");
 }
}
