import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Add one early common-side channel initialization call; retain every existing instruction. */
public final class FieldNetworkBootstrap {
    static final String MOD_ID="eye_judgment_arsenal";
    static final String NETWORK="cn/ssjjdimension/DimensionFieldNetwork";
    static final String ANCHOR="cn/ssjjdimension/DimensionMarkNetwork";
    static final String MOD_ANNOTATION="Lnet/minecraftforge/fml/common/Mod;";
    static final String KEY_ENTRY="cn/judgfire/JudgmentSkillKey.class";
    static final String KEY_CLIENT="cn/ssjjdimension/client/DimensionFieldClient";
    static final String KEY_DESC="(Lnet/minecraftforge/client/event/InputEvent$Key;)Z";

    static void require(boolean condition,String message) {
        if(!condition)throw new IllegalStateException(message);
    }
    static String sha(byte[] data)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    static ClassNode node(byte[] input) {
        var node=new ClassNode();new ClassReader(input).accept(node,0);return node;
    }
    static boolean ownsMod(ClassNode type) {
        if(type.visibleAnnotations==null)return false;
        for(var a:type.visibleAnnotations) {
            if(!MOD_ANNOTATION.equals(a.desc)||a.values==null)continue;
            for(int i=0;i<a.values.size();i+=2)
                if("value".equals(a.values.get(i))&&MOD_ID.equals(a.values.get(i+1)))return true;
        }
        return false;
    }
    static boolean isHook(AbstractInsnNode ins) {
        return ins instanceof MethodInsnNode m&&m.getOpcode()==Opcodes.INVOKESTATIC
            &&NETWORK.equals(m.owner)&&"initNetwork".equals(m.name)&&"()V".equals(m.desc)&&!m.itf;
    }
    static boolean isAnchor(AbstractInsnNode ins) {
        return ins instanceof MethodInsnNode m&&m.getOpcode()==Opcodes.INVOKESTATIC
            &&ANCHOR.equals(m.owner)&&"initNetwork".equals(m.name)&&"()V".equals(m.desc)&&!m.itf;
    }
    static int hooks(ClassNode type) {
        int count=0;
        for(var m:type.methods)for(var ins:m.instructions)if(isHook(ins))count++;
        return count;
    }
    static MethodNode constructor(ClassNode type) {
        var matches=type.methods.stream().filter(m->m.name.equals("<init>")&&m.desc.equals("()V")).toList();
        require(matches.size()==1,"Expected exactly one owning no-arg constructor");
        return matches.get(0);
    }
    static AbstractInsnNode previousCode(AbstractInsnNode n) {
        for(n=n.getPrevious();n!=null&&n.getOpcode()<0;n=n.getPrevious()){}
        return n;
    }
    static AbstractInsnNode nextCode(AbstractInsnNode n) {
        for(n=n.getNext();n!=null&&n.getOpcode()<0;n=n.getNext()){}
        return n;
    }
    /** Deterministic tree serialization discards unused constant-pool additions. */
    static byte[] canonicalWithoutHook(byte[] input) {
        var type=node(input);
        for(var method:type.methods)for(var ins:method.instructions.toArray())
            if(isHook(ins))method.instructions.remove(ins);
        var writer=new ClassWriter(0);type.accept(writer);return writer.toByteArray();
    }
    static byte[] patch(byte[] input) {
        var type=node(input);require(ownsMod(type),"Class is not the requested @Mod owner");
        var ctor=constructor(type);int anchorCount=0;AbstractInsnNode anchor=null;
        boolean initializedThis=false;
        for(var ins:ctor.instructions) {
            if(ins instanceof MethodInsnNode m&&m.getOpcode()==Opcodes.INVOKESPECIAL
                &&"<init>".equals(m.name)&&(m.owner.equals(type.superName)||m.owner.equals(type.name)))initializedThis=true;
            if(isAnchor(ins)) {
                require(initializedThis,"Network anchor precedes this/super initialization");
                anchor=ins;anchorCount++;
            }
        }
        require(anchorCount==1,"Expected exactly one existing early mark network bootstrap");
        int beforeHooks=hooks(type);
        require(beforeHooks<=1,"More than one Dimension Field network bootstrap call in input");
        if(beforeHooks==1) {
            require(isHook(nextCode(anchor)),"Existing Dimension Field bootstrap must immediately follow the known anchor");
            return input;
        }
        // A static ()V call consumes/pushes no stack operands; existing maxs and frames remain valid.
        ctor.instructions.insert(anchor,new MethodInsnNode(Opcodes.INVOKESTATIC,NETWORK,"initNetwork","()V",false));
        var writer=new ClassWriter(new ClassReader(input),0);type.accept(writer);byte[] output=writer.toByteArray();
        var after=node(output);
        require(hooks(after)==1,"Patched class must contain exactly one new bootstrap call");
        require(Arrays.equals(canonicalWithoutHook(input),canonicalWithoutHook(output)),
            "Unexpected change to class structure, original instructions, frames, exception tables or metadata");
        require(Arrays.equals(output,patch(output)),"Bootstrap patch must be byte-for-byte idempotent");
        return output;
    }
    static MethodNode reserved(ClassNode type) {
        var candidates=type.methods.stream().filter(m->m.name.equals("reserved")&&m.desc.equals(KEY_DESC)).toList();
        require(candidates.size()==1,"Expected exactly one original public static skill reservation method");
        var method=candidates.get(0);
        require((method.access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC))==(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC),"Key reservation access differs");
        return method;
    }
    static boolean isKeyHook(AbstractInsnNode i) {
        return i instanceof MethodInsnNode m&&m.getOpcode()==Opcodes.INVOKESTATIC&&m.owner.equals(KEY_CLIENT)
            &&m.name.equals("reserveSkillKey")&&m.desc.equals(KEY_DESC)&&!m.itf;
    }
    static int keyHooks(ClassNode type) {
        int n=0;for(var m:type.methods)for(var i:m.instructions)if(isKeyHook(i))n++;return n;
    }
    static void removeKeyGuard(ClassNode type) {
        var method=reserved(type);var first=method.instructions.getFirst();
        while(first!=null&&first.getOpcode()<0)first=first.getNext();
        if(keyHooks(type)==0)return;
        require(first!=null&&nextCode(first) instanceof MethodInsnNode&&isKeyHook(nextCode(first)),"Existing key guard is not at method entry");
        var hook=nextCode(first);
        require(first instanceof VarInsnNode a&&a.getOpcode()==Opcodes.ALOAD&&a.var==0,"Key guard argument differs");
        var branch=hook.getNext();require(branch instanceof JumpInsnNode j&&j.getOpcode()==Opcodes.IFEQ,"Key guard branch differs");
        var one=branch.getNext();var ret=one.getNext();var label=ret.getNext();var frame=label.getNext();
        require(one.getOpcode()==Opcodes.ICONST_1&&ret.getOpcode()==Opcodes.IRETURN&&label==((JumpInsnNode)branch).label,
            "Key guard must immediately return true or fall through unchanged");
        require(frame instanceof FrameNode f&&f.type==Opcodes.F_SAME,"Expected unchanged initial-locals fallback frame, got "+frame);
        for(var i:new AbstractInsnNode[]{first,hook,branch,one,ret,label,frame})method.instructions.remove(i);
    }
    static byte[] keyCanonicalWithoutGuard(byte[] input) {
        var type=node(input);removeKeyGuard(type);var writer=new ClassWriter(0);type.accept(writer);return writer.toByteArray();
    }
    static byte[] patchKey(byte[] input)throws Exception {
        var type=node(input);require(type.name.equals("cn/judgfire/JudgmentSkillKey"),"Unexpected key owner");
        var method=reserved(type);int count=keyHooks(type);require(count<=1,"Duplicate new key guard");
        if(count==1){var verified=node(input);removeKeyGuard(verified);require(keyHooks(verified)==0,"Existing key guard not completely removed");return input;}
        require(method.maxStack>=1,"Original key method stack capacity unexpectedly empty");
        var fallback=new LabelNode();var guard=new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD,0));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,KEY_CLIENT,"reserveSkillKey",KEY_DESC,false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ,fallback));guard.add(new InsnNode(Opcodes.ICONST_1));guard.add(new InsnNode(Opcodes.IRETURN));
        guard.add(fallback);guard.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
        var first=method.instructions.getFirst();while(first.getOpcode()<0)first=first.getNext();method.instructions.insertBefore(first,guard);
        // No operands survive to the original body; the new branch sees the original initial locals.
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(type.name,method);
        var writer=new ClassWriter(new ClassReader(input),0);type.accept(writer);var output=writer.toByteArray();
        require(keyHooks(node(output))==1,"Expected one new reservation guard");
        require(Arrays.equals(keyCanonicalWithoutGuard(input),keyCanonicalWithoutGuard(output)),"Original skill routing changed beyond its entry guard");
        require(Arrays.equals(output,patchKey(output)),"Reservation guard must be byte-idempotent");
        return output;
    }
    public static void main(String[] args)throws Exception {
        require(args.length==3,"Usage: FieldNetworkBootstrap <baseline.jar> <overlay-dir> <evidence.json>");
        Path input=Path.of(args[0]),overlay=Path.of(args[1]),evidence=Path.of(args[2]);
        String entryName=null;byte[] original=null,keyOriginal=null;
        try(var zip=new ZipFile(input.toFile())) {
            keyOriginal=zip.getInputStream(zip.getEntry(KEY_ENTRY)).readAllBytes();
            var entries=zip.entries();
            while(entries.hasMoreElements()) {
                var e=entries.nextElement();if(!e.getName().endsWith(".class"))continue;
                byte[] bytes=zip.getInputStream(e).readAllBytes();
                var type=new ClassNode();new ClassReader(bytes).accept(type,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                if(!ownsMod(type))continue;
                require(entryName==null,"More than one @Mod owner for "+MOD_ID);
                entryName=e.getName();original=bytes;
            }
        }
        require(original!=null,"No @Mod owner found for "+MOD_ID);
        byte[] output=patch(original);
        require(Arrays.equals(canonicalWithoutHook(original),canonicalWithoutHook(output)),"Final independent tree equivalence check failed");
        Path target=overlay.resolve(entryName);Files.createDirectories(target.getParent());Files.write(target,output);
        var keyOutput=patchKey(keyOriginal);var keyTarget=overlay.resolve(KEY_ENTRY);
        Files.createDirectories(keyTarget.getParent());Files.write(keyTarget,keyOutput);
        Files.createDirectories(evidence.toAbsolutePath().getParent());
        var report=new LinkedHashMap<String,Object>();
        report.put("passed",true);report.put("input",input.toString());report.put("input_sha256",sha(Files.readAllBytes(input)));
        report.put("modified_class",entryName);report.put("class_before_sha256",sha(original));report.put("class_after_sha256",sha(output));
        report.put("inserted_instruction",NETWORK+".initNetwork()V");report.put("insert_after",ANCHOR+".initNetwork()V");
        report.put("constructor","()V");report.put("new_hook_count",hooks(node(output)));
        report.put("common_side_constructor_registration",true);report.put("after_this_or_super_initialization",true);
        report.put("other_instructions_frames_attributes_and_methods_preserved",true);
        report.put("canonical_class_without_new_hook_sha256",sha(canonicalWithoutHook(original)));
        report.put("idempotent_bytes",Arrays.equals(output,patch(output)));report.put("live_client_test",false);
        report.put("modified_classes",List.of(entryName,KEY_ENTRY));
        report.put("key_guard",Map.of("owner",KEY_ENTRY,"inserted_call",KEY_CLIENT+".reserveSkillKey"+KEY_DESC,
            "class_before_sha256",sha(keyOriginal),"class_after_sha256",sha(keyOutput),
            "other_original_instructions_frames_and_metadata_preserved",Arrays.equals(keyCanonicalWithoutGuard(keyOriginal),keyCanonicalWithoutGuard(keyOutput)),
            "byte_idempotent",Arrays.equals(keyOutput,patchKey(keyOutput)),"ASM_basic_verifier",true));
        Files.writeString(evidence,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("PASS one early Dimension Field channel bootstrap and one V-key reservation guard; original class bodies preserved; byte-idempotent");
    }
}
