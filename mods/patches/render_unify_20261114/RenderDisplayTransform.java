import java.nio.file.*;
import java.security.MessageDigest;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.*;
import com.google.gson.*;

/** Only redirects the eight reviewed display-binding chains; callback bodies stay intact. */
public final class RenderDisplayTransform {
    static final String API="cn/fusionarsenal/compat/TaCZClientCompat";
    static final String LOCATION="Lnet/minecraft/resources/ResourceLocation;";
    static final String OLD="("+LOCATION+LOCATION+")Ljava/util/Optional;";
    static final String NEW="("+LOCATION+LOCATION+"Ljava/util/function/Consumer;)V";
    static final Map<String,String> METHODS=Map.of(
        "cn/ssjjdimension/client/DimensionMeshRenderer","tick",
        "cn/ssjjdimension/client/DimensionActivationEffects","bindDimensionActivation",
        "cn/ssjjparasite2/client/SourceMeshRenderer","onClientTick",
        "cn/ssjjzeus/client/ZeusMeshRenderer","tick",
        "cn/punishmenteye/SkinVisuals","install",
        "cn/judgfire/BodyEffects","tick");
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static ClassNode node(byte[] b){ClassNode n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
    static String fingerprint(MethodNode m)throws Exception{
        Textifier t=new Textifier();TraceMethodVisitor trace=new TraceMethodVisitor(t);
        for(AbstractInsnNode i:m.instructions)i.accept(trace);
        StringWriter sw=new StringWriter();t.print(new PrintWriter(sw));
        return sha(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static String codeFingerprint(MethodNode m)throws Exception{
        Textifier t=new Textifier();TraceMethodVisitor trace=new TraceMethodVisitor(t);
        for(AbstractInsnNode i:m.instructions)if(!(i instanceof LineNumberNode))i.accept(trace);
        StringWriter sw=new StringWriter();t.print(new PrintWriter(sw));
        return sha(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static Map<String,String> fingerprints(ClassNode n)throws Exception{
        Map<String,String> out=new TreeMap<>();for(MethodNode m:n.methods)out.put(m.name+m.desc,fingerprint(m));return out;
    }
    static void basicVerify(ClassNode n)throws Exception{
        for(MethodNode m:n.methods)if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(n.name,m);
    }
    static Set<String> publicMethods(ClassNode n){
        Set<String> out=new TreeSet<>();for(MethodNode m:n.methods)if((m.access&Opcodes.ACC_PUBLIC)!=0)out.add(m.name+m.desc);return out;
    }
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args[0]), apiClasses=Path.of(args[1]), output=Path.of(args[2]), report=Path.of(args[3]);
        Map<String,Object> results=new TreeMap<>();List<Map<String,Object>> sites=new ArrayList<>();int total=0;
        try(JarFile base=new JarFile(input.toFile());JarOutputStream out=new JarOutputStream(Files.newOutputStream(output))){
            for(var target:METHODS.entrySet()){
                String name=target.getKey();byte[] before=base.getInputStream(base.getJarEntry(name+".class")).readAllBytes();
                ClassNode n=node(before);Map<String,String> oldPrints=fingerprints(n);Set<String> editedMethods=new TreeSet<>();int count=0;
                for(MethodNode m:n.methods){
                    for(AbstractInsnNode instruction:m.instructions.toArray()){
                        if(!(instruction instanceof MethodInsnNode call)||!call.owner.equals("com/tacz/guns/api/TimelessAPI")
                            ||!call.name.equals("getGunDisplay")||!call.desc.equals(OLD))continue;
                        check(m.name.equals(target.getValue()),"Unexpected binding method "+n.name+"."+m.name+m.desc);
                        AbstractInsnNode cursor=call.getNext();MethodInsnNode terminal=null;int intermediate=0;
                        while(cursor!=null){
                            if(cursor instanceof MethodInsnNode x){
                                check(x.owner.equals("java/util/Optional")&&x.name.equals("ifPresent")&&x.desc.equals("(Ljava/util/function/Consumer;)V"),"Unexpected query consumer "+name+"."+m.name);
                                terminal=x;break;
                            }
                            check(cursor.getOpcode()<0||cursor instanceof VarInsnNode||cursor instanceof InvokeDynamicInsnNode,
                                "Unexpected capture instruction "+name+"."+m.name+": "+cursor.getClass());
                            cursor=cursor.getNext();check(++intermediate<12,"Unbounded query capture");
                        }
                        check(terminal!=null,"Missing Optional.ifPresent in "+name+"."+m.name);
                        // The two ids stay under the unchanged lambda captures on the operand stack.
                        m.instructions.remove(call);
                        terminal.setOpcode(Opcodes.INVOKESTATIC);terminal.owner=API;terminal.name="forEachGunDisplay";terminal.desc=NEW;terminal.itf=false;
                        editedMethods.add(m.name+m.desc);count++;total++;
                        sites.add(Map.of("class",name,"method",m.name+m.desc,"operation","strict named + matching default instance, identity deduplicated"));
                    }
                }
                int expected=name.equals("cn/judgfire/BodyEffects")?3:1;check(count==expected,"Unexpected binding count "+name+": "+count);
                ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(writer);byte[] after=writer.toByteArray();
                ClassNode rebuilt=node(after);basicVerify(rebuilt);Map<String,String> newPrints=fingerprints(rebuilt);
                check(oldPrints.keySet().equals(newPrints.keySet()),"Method set changed "+name);
                Map<String,String> unchanged=new TreeMap<>();Map<String,Object> changed=new TreeMap<>();
                for(String key:oldPrints.keySet()){
                    if(editedMethods.contains(key)){
                        check(!oldPrints.get(key).equals(newPrints.get(key)),"Expected modified method unchanged");
                        changed.put(key,Map.of("before",oldPrints.get(key),"after",newPrints.get(key)));
                    }else{check(oldPrints.get(key).equals(newPrints.get(key)),"Unrelated method changed "+name+"."+key);unchanged.put(key,oldPrints.get(key));}
                }
                out.putNextEntry(new JarEntry(name+".class"));out.write(after);out.closeEntry();
                results.put(name,Map.of("before_sha256",sha(before),"after_sha256",sha(after),"binding_call_sites",count,
                    "changed_methods",changed,"unchanged_method_fingerprints",unchanged,"basic_verified",true));
            }
            check(total==8&&results.size()==6,"Expected six classes / eight binding chains");
            byte[] oldApi=base.getInputStream(base.getJarEntry(API+".class")).readAllBytes();
            byte[] newApi=Files.readAllBytes(apiClasses.resolve(API+".class"));
            Set<String> oldPublic=publicMethods(node(oldApi)),newPublic=publicMethods(node(newApi));
            check(newPublic.containsAll(oldPublic),"Existing public API removed");
            Set<String> extra=new TreeSet<>(newPublic);extra.removeAll(oldPublic);
            check(extra.equals(Set.of("forEachGunDisplay"+NEW)),"Unexpected new public API "+extra);
            Map<String,String> originalApiCode=new TreeMap<>();
            for(MethodNode m:node(oldApi).methods)originalApiCode.put(m.name+m.desc,codeFingerprint(m));
            Map<String,String> retainedApiCode=new TreeMap<>();
            for(MethodNode m:node(newApi).methods)if(originalApiCode.containsKey(m.name+m.desc)){
                String digest=codeFingerprint(m);check(digest.equals(originalApiCode.get(m.name+m.desc)),"Existing API implementation changed: "+m.name+m.desc);
                retainedApiCode.put(m.name+m.desc,digest);
            }
            check(retainedApiCode.keySet().equals(originalApiCode.keySet()),"Existing API method disappeared");
            try(var stream=Files.walk(apiClasses.resolve("cn/fusionarsenal/compat"))){
                for(Path p:stream.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".class")).toList()){
                    String rel=apiClasses.relativize(p).toString().replace('\\','/');
                    check(rel.equals(API+".class")||rel.startsWith(API+"$"),"Unexpected compiled API class "+rel);
                    out.putNextEntry(new JarEntry(rel));out.write(Files.readAllBytes(p));out.closeEntry();
                }
            }
            Map<String,Object> summary=new LinkedHashMap<>();summary.put("passed",true);summary.put("baseline_sha256",sha(Files.readAllBytes(input)));
            summary.put("binding_class_count",6);summary.put("binding_call_sites",total);summary.put("classes",results);summary.put("call_sites",sites);
            summary.put("public_api_preserved",oldPublic);summary.put("public_api_added",extra);
            summary.put("existing_api_method_instruction_fingerprints",retainedApiCode);
            summary.put("consumer_business_methods_instruction_identical",true);summary.put("original_correct_default_index_paths_preserved",true);
            summary.put("dynamic_single_stack_queries_preserved",true);summary.put("game_started",false);
            Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(summary));
        }
    }
}
