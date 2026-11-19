import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.*;

/** Replace only the obsolete reflection/catch region of the frozen production bake. */
public final class PatchPhantomTransform {
    static final String OWNER="cn/ssjjzeus/client/ZeusPhantomWeapon";
    static final String HELPER="cn/ssjjcompat/NativeGunItemTransforms";
    static final String APPLY="(Lnet/minecraft/world/item/ItemDisplayContext;Lcom/tacz/guns/client/resource/pojo/TransformScale;Lcom/tacz/guns/client/model/BedrockGunModel;Lcom/mojang/blaze3d/vertex/PoseStack;)V";
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static String text(MethodNode method){var p=new Textifier();method.accept(new TraceMethodVisitor(p));var out=new StringWriter();p.print(new PrintWriter(out));return out.toString();}
    static AbstractInsnNode real(AbstractInsnNode n){while(n!=null&&n.getOpcode()<0)n=n.getNext();return n;}
    // Expand before deleting a handler: a following compressed SAME frame may
    // otherwise implicitly depend on the handler's now-deleted FULL frame.
    static ClassNode read(byte[] bytes){var n=new ClassNode();new ClassReader(bytes).accept(n,ClassReader.EXPAND_FRAMES);return n;}
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args[0]),output=Path.of(args[1]),report=Path.of(args[2]);
        byte[] original;
        try(var jar=new ZipFile(input.toFile())){original=jar.getInputStream(jar.getEntry(OWNER+".class")).readAllBytes();}
        ClassNode type=read(original);check(type.name.equals(OWNER),"Unexpected class owner");
        Map<String,String> methods=new TreeMap<>();for(MethodNode m:type.methods)methods.put(m.name+m.desc,text(m));
        MethodNode bake=type.methods.stream().filter(m->m.name.equals("bake")).findFirst().orElseThrow();
        List<TryCatchBlockNode> blocks=bake.tryCatchBlocks.stream().filter(t->"java/lang/ReflectiveOperationException".equals(t.type)).toList();
        check(blocks.size()==1,"Expected exactly one original reflection catch region");
        TryCatchBlockNode block=blocks.get(0);
        check(real(block.start) instanceof LdcInsnNode s && s.cst.equals("com.tacz.guns.client.renderer.item.GunItemTransforms"),"Reflection region no longer matches frozen production");
        check(real(block.end) instanceof JumpInsnNode,"Expected normal continuation after reflection");
        JumpInsnNode jump=(JumpInsnNode)real(block.end);check(jump.getOpcode()==Opcodes.GOTO,"Expected unconditional catch skip");
        LabelNode continuation=jump.label;
        int start=bake.instructions.indexOf(block.start),end=bake.instructions.indexOf(continuation);
        check(start>=0 && end>start && bake.instructions.indexOf(block.handler)>start && bake.instructions.indexOf(block.handler)<end,"Unexpected catch layout");
        check(bake.tryCatchBlocks.size()==1,"Unexpected additional bake catch regions");
        Set<AbstractInsnNode> removed=Collections.newSetFromMap(new IdentityHashMap<>());
        for(AbstractInsnNode n=block.start;n!=continuation;n=n.getNext())removed.add(n);
        check(removed.stream().anyMatch(n->n instanceof MethodInsnNode c&&c.owner.equals("java/lang/reflect/Method")&&c.name.equals("invoke")),"No obsolete invocation found");
        List<AbstractInsnNode> outside=new ArrayList<>();for(AbstractInsnNode n:bake.instructions)if(!removed.contains(n))outside.add(n);
        var replacement=new InsnList();
        replacement.add(new FieldInsnNode(Opcodes.GETSTATIC,"net/minecraft/world/item/ItemDisplayContext","THIRD_PERSON_RIGHT_HAND","Lnet/minecraft/world/item/ItemDisplayContext;"));
        replacement.add(new VarInsnNode(Opcodes.ALOAD,3));
        replacement.add(new VarInsnNode(Opcodes.ALOAD,5));
        replacement.add(new VarInsnNode(Opcodes.ALOAD,7));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"apply",APPLY,false));
        bake.instructions.insertBefore(block.start,replacement);
        for(AbstractInsnNode n:new ArrayList<>(removed))bake.instructions.remove(n);
        bake.tryCatchBlocks.remove(block);
        if(bake.localVariables!=null)bake.localVariables.removeIf(v->removed.contains(v.start)||removed.contains(v.end));
        check(bake.visibleLocalVariableAnnotations==null&&bake.invisibleLocalVariableAnnotations==null,"Unexpected local-variable annotations");
        for(AbstractInsnNode n:outside)check(bake.instructions.contains(n),"An unrelated bake instruction was removed");
        String expectedBake=text(bake);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);byte[] patched=writer.toByteArray();
        ClassNode after=read(patched);int untouched=0,verified=0;
        for(MethodNode m:after.methods){
            if(m.name.equals("bake"))check(text(m).equals(expectedBake),"Writer changed patched bake instructions");
            else{check(text(m).equals(methods.get(m.name+m.desc)),"Unrelated method changed: "+m.name+m.desc);untouched++;}
            if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){new Analyzer<BasicValue>(new BasicVerifier()).analyze(after.name,m);verified++;}
        }
        check(!new String(patched,java.nio.charset.StandardCharsets.ISO_8859_1).contains("Missing native phantom third-person transform"),"Obsolete failure site retained");
        Path file=output.resolve(OWNER+".class");Files.createDirectories(file.getParent());Files.write(file,patched);
        var result=new LinkedHashMap<String,Object>();result.put("passed",true);result.put("input_jar",input.toString());result.put("class",OWNER+".class");
        result.put("original_class_sha256",sha(original));result.put("patched_class_sha256",sha(patched));
        result.put("changed_method","bake");result.put("unrelated_methods_identical",untouched);result.put("unchanged_bake_instruction_nodes",outside.size());
        result.put("removed_reflection_and_catch_nodes",removed.size());result.put("basic_verifier_methods",verified);result.put("runtime_game_test",false);
        Files.createDirectories(report.toAbsolutePath().getParent());Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("PASS precise phantom transform patch; "+untouched+" unrelated methods identical; "+verified+" methods verified");
    }
}
