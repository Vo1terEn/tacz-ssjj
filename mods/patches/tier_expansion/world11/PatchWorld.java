import java.nio.file.*;import java.util.*;import java.util.jar.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;
public class PatchWorld implements Opcodes{
 static final String ROOT="cn/judgfire/",POOL=ROOT+"WorldMeshReuse",SCAN=ROOT+"CrownScanReuse";static int hooks;
 static void call(InsnList x,String owner,String name,String desc){x.add(new MethodInsnNode(INVOKESTATIC,owner,name,desc,false));}
 static void clear(MethodNode m){m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();}
 static MethodNode wrap(ClassNode n,MethodNode original,String renamed,String gate,String begin,String end,int beginVar){
  String old=original.name;original.name=renamed;var wrapper=new MethodNode(ASM9,original.access,old,original.desc,null,null);var x=wrapper.instructions;
  if(gate!=null){call(x,POOL,gate,"()Z");var enabled=new LabelNode();x.add(new JumpInsnNode(IFNE,enabled));x.add(new InsnNode(RETURN));x.add(enabled);}
  if(beginVar>=0)x.add(new VarInsnNode(ALOAD,beginVar));call(x,beginVar>=0?SCAN:POOL,begin,beginVar>=0?"(Lnet/minecraft/world/entity/LivingEntity;)V":"()V");
  var start=new LabelNode();var finish=new LabelNode();var handler=new LabelNode();x.add(start);
  int index=0;for(Type type:Type.getArgumentTypes(original.desc)){x.add(new VarInsnNode(type.getOpcode(ILOAD),index));index+=type.getSize();}
  call(x,n.name,renamed,original.desc);Type ret=Type.getReturnType(original.desc);if(ret.getSort()!=Type.VOID)x.add(new VarInsnNode(ret.getOpcode(ISTORE),index));
  x.add(finish);call(x,beginVar>=0?SCAN:POOL,end,"()V");if(ret.getSort()!=Type.VOID)x.add(new VarInsnNode(ret.getOpcode(ILOAD),index));x.add(new InsnNode(ret.getOpcode(IRETURN)));
  x.add(handler);x.add(new VarInsnNode(ASTORE,index+2));call(x,beginVar>=0?SCAN:POOL,end,"()V");x.add(new VarInsnNode(ALOAD,index+2));x.add(new InsnNode(ATHROW));wrapper.tryCatchBlocks.add(new TryCatchBlockNode(start,finish,handler,null));hooks++;return wrapper;
 }
 public static void main(String[]args)throws Exception{Path out=Path.of(args[1]);
  try(var jar=new JarFile(args[0])){for(String simple:List.of("SkinWorldFlames","NativeWorldFlames","WorldFlameRenderType","CrownTargets","EmperorCrown")){
   ClassReader reader=new ClassReader(jar.getInputStream(jar.getJarEntry(ROOT+simple+".class")));var n=new ClassNode();reader.accept(n,ClassReader.EXPAND_FRAMES);var extra=new ArrayList<MethodNode>();
   for(var m:n.methods){
    if(simple.equals("SkinWorldFlames")){
     if(m.name.equals("draw")&&Type.getArgumentTypes(m.desc).length==8)extra.add(wrap(n,m,"drawScoped", "enabled","begin","end",-1));
     if(m.name.equals("add")){
      String old=m.name;m.name="addSnapshot";var w=new MethodNode(ASM9,ACC_PRIVATE|ACC_STATIC,old,m.desc,null,null);var x=w.instructions;
      call(x,POOL,"active","()Z");var fallback=new LabelNode();x.add(new JumpInsnNode(IFEQ,fallback));x.add(new VarInsnNode(ALOAD,0));x.add(new VarInsnNode(ALOAD,2));
      for(String field:List.of("center","velocity","size","euler","color","uv","custom")){x.add(new VarInsnNode(ALOAD,3));x.add(new FieldInsnNode(GETFIELD,ROOT+"SkinWorldFlames$State",field,"[F"));}
      x.add(new VarInsnNode(DLOAD,4));x.add(new VarInsnNode(DLOAD,6));for(int i=8;i<=12;i++)x.add(new VarInsnNode(ALOAD,i));
      call(x,POOL,"add","(Ljava/util/List;Lcn/judgfire/SkinWorldFlames$Layer;[F[F[F[F[F[F[FDDLorg/joml/Vector3f;Lorg/joml/Vector3f;Lcn/judgfire/SkinWorldFlames$Flight;Lorg/joml/Matrix4f;[F)Z");
      x.add(new JumpInsnNode(IFEQ,fallback));x.add(new InsnNode(RETURN));x.add(fallback);int index=0;for(Type t:Type.getArgumentTypes(m.desc)){x.add(new VarInsnNode(t.getOpcode(ILOAD),index));index+=t.getSize();}call(x,n.name,m.name,m.desc);x.add(new InsnNode(RETURN));extra.add(w);hooks++;
     }
     if(m.name.equals("impactWindow")){clear(m);call(m.instructions,n.name,"ensure","()V");m.instructions.add(new FieldInsnNode(GETSTATIC,n.name,"current","Lcn/judgfire/SkinWorldFlames$Data;"));m.instructions.add(new VarInsnNode(ILOAD,0));call(m.instructions,POOL,"retention","(Lcn/judgfire/SkinWorldFlames$Data;I)D");m.instructions.add(new InsnNode(DRETURN));hooks++;}
     if(m.name.equals("resetWorld")||m.name.equals("reload")){var pre=new InsnList();call(pre,POOL,"clear","()V");call(pre,ROOT+"WorldScenePlan","clear","()V");m.instructions.insert(pre);hooks++;}
    }
    if(simple.equals("NativeWorldFlames")&&m.name.equals("draw")&&Type.getArgumentTypes(m.desc).length==8){var pre=new InsnList();call(pre,POOL,"enabled","()Z");var yes=new LabelNode();pre.add(new JumpInsnNode(IFNE,yes));pre.add(new InsnNode(RETURN));pre.add(yes);m.instructions.insert(pre);hooks++;}
    if(simple.equals("WorldFlameRenderType")){
     if(m.name.equals("beginWorldPass")){clear(m);call(m.instructions,ROOT+"WorldScenePlan","begin","()V");m.instructions.add(new InsnNode(RETURN));hooks++;}
     if(m.name.equals("invalidateScene")){var pre=new InsnList();call(pre,ROOT+"WorldScenePlan","clear","()V");m.instructions.insert(pre);hooks++;}
    }
    if(simple.equals("CrownTargets")&&m.name.equals("collect")){
     extra.add(wrap(n,m,"collectCached",null,"begin","end",1));
     for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals(ROOT+"FireCombatStages")&&c.name.equals("inRange")){c.owner=SCAN;hooks++;}
    }
    if(simple.equals("EmperorCrown")&&m.name.equals("tick"))for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals("java/util/List")&&c.name.equals("sort")){c.setOpcode(INVOKESTATIC);c.owner=SCAN;c.name="first";c.desc="(Ljava/util/List;Ljava/util/Comparator;)V";c.itf=false;hooks++;}
   }
   n.methods.addAll(extra);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);n.accept(writer);byte[] bytes=writer.toByteArray();var check=new ClassNode();new ClassReader(bytes).accept(check,0);for(var method:check.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(n.name,method);Path p=out.resolve(n.name+".class");Files.createDirectories(p.getParent());Files.write(p,bytes);
  }}if(hooks!=11)throw new AssertionError("Expected11 hooks; got"+hooks);System.out.println("PASS11 boundedmesh/capture/retention/Crown hooks; allmethodsstackverified");
 }
}
