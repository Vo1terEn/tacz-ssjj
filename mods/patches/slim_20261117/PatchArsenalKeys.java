import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;

/** Patches only six frozen production constructors and their registration calls. */
public final class PatchArsenalKeys {
    static final String HELPER="cn/ssjjcompat/ArsenalKeyBindings";
    static final String MAPPING="net/minecraft/client/KeyMapping";
    static final String EVENT="net/minecraftforge/client/event/RegisterKeyMappingsEvent";
    static final String TYPE="Lcom/mojang/blaze3d/platform/InputConstants$Type;";
    static final String CONTEXT="Lnet/minecraftforge/client/settings/IKeyConflictContext;";
    static final String KEY="L"+MAPPING+";";
    record Contract(String owner,String id,String category,int code,boolean inGame,String registration) {}
    static final List<Contract> CONTRACTS=List.of(
        new Contract("cn/punishmenteye/SkillInput","key.eyeofpunishment.skill","key.categories.eyeofpunishment",86,false,"$Registration"),
        new Contract("cn/ssjjzeus/client/ZeusAdventKeys","key.ssjj_zeus.advent","key.categories.eyeofpunishment",86,false,"$Keys"),
        new Contract("cn/ssjjparasite2/client/ParasiteCannonClient","key.ssjjparasite2.cannon","key.categories.eyeofpunishment",86,false,"$Registration"),
        new Contract("cn/ssjjdimension/client/DimensionFieldClient","key.ssjj_dimension.field","key.categories.eyeofpunishment",86,false,"$Keys"),
        new Contract("cn/ssjjhyper/client/HyperCoreClient","key.ssjj_hyper.energy_core","key.categories.ssjj_hyper",71,true,"$Keys"),
        new Contract("cn/judgfire/BurstClient","key.judgfire.burst","key.categories.judgfire",86,true,"$Keys")
    );
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static String text(MethodNode method){
        Textifier printer=new Textifier();method.accept(new TraceMethodVisitor(printer));
        StringWriter out=new StringWriter();printer.print(new PrintWriter(out));return out.toString();
    }
    static String signature(MethodNode method){return method.name+method.desc;}
    static AbstractInsnNode next(AbstractInsnNode n){do{n=n.getNext();}while(n!=null&&n.getOpcode()<0);return n;}
    static void write(Path output,String owner,byte[] bytes)throws Exception{
        Path path=output.resolve(owner+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);
    }
    static int patchConstructor(MethodNode method,Contract c){
        int edits=0;
        for(AbstractInsnNode node:method.instructions.toArray()){
            if(!(node instanceof TypeInsnNode allocation)||node.getOpcode()!=Opcodes.NEW||!allocation.desc.equals(MAPPING))continue;
            AbstractInsnNode dup=next(node),id=next(dup),cursor=next(id);
            require(dup.getOpcode()==Opcodes.DUP,c.owner+": constructor DUP");
            require(id instanceof LdcInsnNode&&c.id.equals(((LdcInsnNode)id).cst),c.owner+": translation key");
            if(c.inGame){
                require(cursor instanceof FieldInsnNode f&&f.owner.equals("net/minecraftforge/client/settings/KeyConflictContext")&&f.name.equals("IN_GAME"),c.owner+": context");cursor=next(cursor);
            }
            require(cursor instanceof FieldInsnNode f&&f.name.equals("KEYSYM")&&f.owner.equals("com/mojang/blaze3d/platform/InputConstants$Type"),c.owner+": input type");
            AbstractInsnNode code=next(cursor),category=next(code),call=next(category);
            require(code instanceof IntInsnNode&&((IntInsnNode)code).operand==c.code,c.owner+": default key");
            require(category instanceof LdcInsnNode&&c.category.equals(((LdcInsnNode)category).cst),c.owner+": old category");
            String parameters="Ljava/lang/String;"+(c.inGame?CONTEXT:"")+TYPE+"I";
            require(call instanceof MethodInsnNode invoke&&invoke.owner.equals(MAPPING)&&invoke.name.equals("<init>")&&invoke.desc.equals("("+parameters+"Ljava/lang/String;)V"),c.owner+": constructor descriptor");
            method.instructions.remove(node);method.instructions.remove(dup);method.instructions.remove(category);
            method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"create","("+parameters+")"+KEY,false));edits++;
        }
        return edits;
    }
    static int patchRegistration(MethodNode method){
        int edits=0;
        for(AbstractInsnNode node:method.instructions.toArray())if(node instanceof MethodInsnNode call&&call.owner.equals(EVENT)&&call.name.equals("register")&&call.desc.equals("("+KEY+")V")){
            method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"register","(L"+EVENT+";"+KEY+")V",false));edits++;
        }
        return edits;
    }
    static String patch(ZipFile zip,Path output,Contract c,boolean registration)throws Exception{
        String owner=c.owner+(registration?c.registration:"");
        byte[] before=zip.getInputStream(zip.getEntry(owner+".class")).readAllBytes();
        ClassReader reader=new ClassReader(before);ClassNode clazz=new ClassNode();reader.accept(clazz,0);
        Map<String,String> original=new LinkedHashMap<>();for(MethodNode method:clazz.methods)original.put(signature(method),text(method));
        Set<String> edited=new LinkedHashSet<>();int edits=0;
        for(MethodNode method:clazz.methods){
            int count=registration?patchRegistration(method):(method.name.equals("<clinit>")?patchConstructor(method,c):0);
            if(count>0){edits+=count;edited.add(signature(method));}
        }
        require(edits==1,owner+": expected exactly one patch, got "+edits);
        ClassWriter writer=new ClassWriter(reader,0);clazz.accept(writer);byte[] bytes=writer.toByteArray();
        ClassNode check=new ClassNode();new ClassReader(bytes).accept(check,0);
        int unchanged=0;for(MethodNode method:check.methods)if(!edited.contains(signature(method))){
            require(original.get(signature(method)).equals(text(method)),owner+": unrelated method changed "+signature(method));unchanged++;
        }
        // BasicVerifier checks the changed stack without substituting stub game APIs.
        for(MethodNode method:check.methods){
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)
                new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicVerifier()).analyze(check.name,method);
        }
        write(output,owner,bytes);
        return "{\"class\":\""+owner+"\",\"patched_method\":\""+edited.iterator().next()+"\",\"unrelated_methods_unchanged\":"+unchanged+",\"basic_verifier\":true}";
    }
    public static void main(String[] args)throws Exception{
        require(args.length==3,"input jar, output directory, evidence json required");
        Path output=Path.of(args[1]);List<String> evidence=new ArrayList<>();
        try(ZipFile zip=new ZipFile(args[0])){for(Contract c:CONTRACTS){evidence.add(patch(zip,output,c,false));evidence.add(patch(zip,output,c,true));}}
        Path report=Path.of(args[2]);Files.createDirectories(report.getParent());
        Files.writeString(report,"{\"passed\":true,\"active_bindings\":6,\"unchanged_defaults_contexts_ids_and_input_handlers\":true,\"classes\":["+String.join(",",evidence)+"]}\n");
        System.out.println("PASS six key constructors and six registration calls; all unrelated method instructions unchanged");
    }
}
