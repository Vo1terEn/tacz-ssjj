import java.nio.file.*;import java.util.*;import java.util.jar.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;import org.objectweb.asm.util.*;
/** Compose onto the delivered renderer, preserving every unrelated method/instruction. */
public final class PatchReuse implements Opcodes {
 static String R="cn/judgfire/EffectRenderReuse",C="cn/judgfire/EffectPerformanceConfig";
 static void call(InsnList x,String owner,String name,String desc){x.add(new MethodInsnNode(INVOKESTATIC,owner,name,desc,false));}
 static String body(MethodNode method){var trace=new Textifier();method.accept(new TraceMethodVisitor(trace));return trace.text.toString();}
 public static void main(String[]args)throws Exception{Path out=Path.of(args[1]);int count=0,unchanged=0;
  try(var jar=new JarFile(args[0])){for(String name:List.of("cn/judgfire/DefaultFlameEffects","cn/judgfire/BodyEffectBinding")){
   var reader=new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class")));var n=new ClassNode();reader.accept(n,0);var originals=new LinkedHashMap<String,String>();for(var method:n.methods)originals.put(method.name+method.desc,body(method));var add=new ArrayList<MethodNode>();
   for(var m:n.methods){
    if(name.endsWith("DefaultFlameEffects")&&m.name.equals("renderDefinition")){
     String old=m.name;m.name="renderDefinitionUnpooled";m.access=ACC_PRIVATE|ACC_STATIC;var w=new MethodNode(ASM9,ACC_STATIC,old,m.desc,null,null);var x=w.instructions;
     call(x,C,"nativeGeometry","()Z");var enabled=new LabelNode();x.add(new JumpInsnNode(IFNE,enabled));x.add(new InsnNode(RETURN));x.add(enabled);x.add(new FrameNode(F_SAME,0,null,0,null));
     call(x,R,"begin","()V");LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();x.add(start);
     x.add(new VarInsnNode(ALOAD,0));x.add(new VarInsnNode(ILOAD,1));x.add(new VarInsnNode(ALOAD,2));x.add(new VarInsnNode(ALOAD,3));x.add(new VarInsnNode(LLOAD,4));x.add(new VarInsnNode(ALOAD,6));call(x,name,m.name,m.desc);x.add(end);call(x,R,"end","()V");x.add(new InsnNode(RETURN));
     x.add(handler);x.add(new FrameNode(F_SAME1,0,null,1,new Object[]{"java/lang/Throwable"}));x.add(new VarInsnNode(ASTORE,7));call(x,R,"end","()V");x.add(new VarInsnNode(ALOAD,7));x.add(new InsnNode(ATHROW));w.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));add.add(w);count++;
    }
    if(name.endsWith("DefaultFlameEffects")&&m.name.equals("deform")&&m.desc.equals("([[FILcn/judgfire/OriginalMotion$Frame;)[[F")){
     m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();m.instructions.add(new VarInsnNode(ALOAD,0));m.instructions.add(new VarInsnNode(ILOAD,1));m.instructions.add(new VarInsnNode(ALOAD,2));call(m.instructions,R,"rigid",m.desc);m.instructions.add(new InsnNode(ARETURN));count++;
    }
    if(name.endsWith("DefaultFlameEffects")&&m.name.equals("reload")){var pre=new InsnList();call(pre,R,"clear","()V");m.instructions.insert(pre);count++;}
    if(name.endsWith("BodyEffectBinding")&&m.name.equals("deform")){
     String old=m.name;m.name="deformUnpooled";m.access=ACC_PRIVATE;var w=new MethodNode(ASM9,ACC_PUBLIC,old,m.desc,null,null);var x=w.instructions;call(x,R,"active","()Z");var plain=new LabelNode();x.add(new JumpInsnNode(IFEQ,plain));x.add(new VarInsnNode(ALOAD,0));x.add(new VarInsnNode(ALOAD,1));x.add(new VarInsnNode(ALOAD,2));call(x,R,"binding","(Lcn/judgfire/BodyEffectBinding;[[FLcn/judgfire/OriginalMotion$Frame;)[[F");x.add(new InsnNode(ARETURN));x.add(plain);x.add(new FrameNode(F_SAME,0,null,0,null));x.add(new VarInsnNode(ALOAD,0));x.add(new VarInsnNode(ALOAD,1));x.add(new VarInsnNode(ALOAD,2));x.add(new MethodInsnNode(INVOKESPECIAL,name,m.name,m.desc,false));x.add(new InsnNode(ARETURN));add.add(w);count++;
    }
   }
   n.methods.addAll(add);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);n.accept(writer);byte[] bytes=writer.toByteArray();var check=new ClassNode();new ClassReader(bytes).accept(check,0);for(var m:check.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(n.name,m);
   for(var entry:originals.entrySet()){String signature=entry.getKey();if(name.endsWith("DefaultFlameEffects")&&(signature.startsWith("deform(")||signature.startsWith("reload(")))continue;String wanted=signature;if(signature.startsWith("renderDefinition("))wanted="renderDefinitionUnpooled"+signature.substring("renderDefinition".length());if(name.endsWith("BodyEffectBinding")&&signature.startsWith("deform("))wanted="deformUnpooled"+signature.substring("deform".length());final String target=wanted;var method=check.methods.stream().filter(v->(v.name+v.desc).equals(target)).findFirst().orElseThrow();if(!entry.getValue().equals(body(method)))throw new AssertionError("Unrelated original instructions changed: "+name+"/"+signature);unchanged++;}
   var p=out.resolve(name+".class");Files.createDirectories(p.getParent());Files.write(p,bytes);
  }}if(count!=4)throw new AssertionError(count);System.out.println("PASS 4 exact renderer/binding hooks; "+unchanged+" original method bodies (including whole renderer and binding fallback) instruction-identical; scope finally stack verified");
 }
}
