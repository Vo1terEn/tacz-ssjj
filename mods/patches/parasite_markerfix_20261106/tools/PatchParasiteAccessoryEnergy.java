import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import com.google.gson.GsonBuilder;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Add two narrow supplier calls to the frozen installed class without recompiling it. */
public final class PatchParasiteAccessoryEnergy implements Opcodes {
    static final String OWNER="cn/fusionarsenal/EyeAccessoryEnergy";
    static final String HELPER="cn/ssjjparasite2/client/ParasiteAccessoryEnergy";
    static final Map<String,String> TARGETS=Map.of("strength","(Lnet/minecraft/world/item/ItemStack;)D",
        "resistanceOrPenetration","(Lnet/minecraft/world/item/ItemStack;Z)D");
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static byte[] methodBytes(MethodNode method){var w=new ClassWriter(0);w.visit(V17,ACC_PUBLIC,"MethodFingerprint",null,"java/lang/Object",null);method.accept(w);w.visitEnd();return w.toByteArray();}
    static List<AbstractInsnNode> code(MethodNode m){return Arrays.stream(m.instructions.toArray()).filter(i->i.getOpcode()>=0).toList();}
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args[0]),output=Path.of(args[1]),reportPath=Path.of(args[2]);byte[] original;
        try(var z=new ZipFile(input.toFile())){original=z.getInputStream(z.getEntry(OWNER+".class")).readAllBytes();}
        var reader=new ClassReader(original);var before=new ClassNode();reader.accept(before,0);
        if(!before.name.equals(OWNER)||new String(original,java.nio.charset.StandardCharsets.ISO_8859_1).contains(HELPER))throw new AssertionError("Patch requires the unpatched frozen class");
        var initial=new HashMap<String,byte[]>();for(var m:before.methods)initial.put(m.name+m.desc,methodBytes(m));
        var details=new ArrayList<Map<String,Object>>();int patched=0;
        for(var m:before.methods)if(m.desc.equals(TARGETS.get(m.name))){
            if((m.access&ACC_STATIC)==0)throw new AssertionError("Expected static supplier");
            var returns=Arrays.stream(m.instructions.toArray()).filter(i->i.getOpcode()==DRETURN).toList();
            if(returns.size()!=1)throw new AssertionError("Expected unique normal supplier return "+m.name);
            int oldCount=code(m).size();var hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));
            if(m.name.equals("resistanceOrPenetration"))hook.add(new VarInsnNode(ILOAD,1));
            hook.add(new MethodInsnNode(INVOKESTATIC,HELPER,m.name,m.desc,false));hook.add(new InsnNode(DADD));m.instructions.insertBefore(returns.get(0),hook);
            details.add(Map.of("method",m.name,"descriptor",m.desc,"before_opcodes",oldCount,"after_opcodes",code(m).size(),"normal_returns",1));patched++;
        }
        if(patched!=2)throw new AssertionError("Expected two supplier methods");
        var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);before.accept(writer);byte[] result=writer.toByteArray();
        var after=new ClassNode();new ClassReader(result).accept(after,0);int unchanged=0;
        for(var m:after.methods){
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(after.name,m);
            String key=m.name+m.desc;boolean target=m.desc.equals(TARGETS.get(m.name));
            if(target){var calls=Arrays.stream(m.instructions.toArray()).filter(i->i instanceof MethodInsnNode n&&n.owner.equals(HELPER)).toList();if(calls.size()!=1)throw new AssertionError("Single bridge call required");
                var call=calls.get(0);var add=call.getNext();if(add.getOpcode()!=DADD)throw new AssertionError("Bridge must add to original return value");m.instructions.remove(add);
                if(m.name.equals("resistanceOrPenetration")){var bool=call.getPrevious();if(!(bool instanceof VarInsnNode v)||v.getOpcode()!=ILOAD||v.var!=1)throw new AssertionError();m.instructions.remove(bool);}
                var stack=call.getPrevious();if(!(stack instanceof VarInsnNode v)||v.getOpcode()!=ALOAD||v.var!=0)throw new AssertionError();m.instructions.remove(stack);m.instructions.remove(call);
                var source=new ClassNode();reader.accept(source,0);var old=source.methods.stream().filter(x->x.name.equals(m.name)&&x.desc.equals(m.desc)).findFirst().orElseThrow();m.maxStack=old.maxStack;
            }else unchanged++;
            if(!Arrays.equals(initial.get(key),methodBytes(m)))throw new AssertionError("Original method changed beyond supplier bridge: "+key);
        }
        Files.createDirectories(output.getParent());Files.write(output,result);
        var report=new LinkedHashMap<String,Object>();report.put("passed",true);report.put("class_entry",OWNER+".class");report.put("input_mod_sha256",sha(Files.readAllBytes(input)));report.put("before_class_sha256",sha(original));report.put("after_class_sha256",sha(result));report.put("patched_methods",details);report.put("other_methods_preserved",unchanged);report.put("original_methods_equal_after_removing_hooks",true);report.put("basic_verifier_passed",true);report.put("no_attribute_registration_or_core_math_change",true);
        Files.createDirectories(reportPath.getParent());Files.writeString(reportPath,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");System.out.println("PASS two narrow accessory-energy supplier hooks; all original method code preserved");
    }
}
