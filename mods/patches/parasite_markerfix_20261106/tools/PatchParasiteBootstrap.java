import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import com.google.gson.GsonBuilder;

/** Patch only the owning constructor and the names of two existing listeners. */
public final class PatchParasiteBootstrap {
    static final String MAIN="cn/fusionarsenal/FusionArsenal";
    static final String BOOT="cn/ssjjparasite2/client/ParasiteNetworkBootstrap";
    static final String DESC="(Lnet/minecraftforge/client/event/RegisterKeyMappingsEvent;)V";
    static final Map<String,String> KEYS=Map.of(
        "cn/judgfire/BurstClient$Keys","registerBurstKeys",
        "cn/judgfire/CrownInput$Keys","registerCrownKeys");
    static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static byte[] patchMain(byte[] input){
        var writer=new ClassWriter(0);int[] constructors={0},config={0},modules={0},bootstrap={0};
        new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] x){
                var next=super.visitMethod(a,n,d,s,x);
                if(!n.equals("<init>")||!d.equals("()V"))return next;
                constructors[0]++;
                return new MethodVisitor(Opcodes.ASM9,next){
                    @Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
                        if(owner.equals("cn/fusionarsenal/config/ArsenalConfig")&&name.equals("register")&&desc.equals("()V"))config[0]++;
                        if(owner.equals("cn/fusionarsenal/api/ModuleRegistry")&&name.equals("initialize")&&desc.equals("()V"))modules[0]++;
                        if(owner.equals(BOOT)&&name.equals("init")&&desc.equals("()V"))bootstrap[0]++;
                        super.visitMethodInsn(op,owner,name,desc,itf);
                    }
                    @Override public void visitInsn(int op){
                        if(op==Opcodes.RETURN&&bootstrap[0]==0){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,BOOT,"init","()V",false);bootstrap[0]++;
                        }
                        super.visitInsn(op);
                    }
                };
            }
        },0);
        if(constructors[0]!=1||config[0]!=1||modules[0]!=1||bootstrap[0]!=1)
            throw new IllegalStateException("Unexpected main constructor: "+Arrays.toString(new int[]{constructors[0],config[0],modules[0],bootstrap[0]}));
        return writer.toByteArray();
    }
    static byte[] patchKeys(String owner,byte[] input){
        var writer=new ClassWriter(0);int[] changed={0};
        new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] x){
                if(n.equals("register")&&d.equals(DESC)){n=KEYS.get(owner);changed[0]++;}
                return super.visitMethod(a,n,d,s,x);
            }
        },0);
        if(changed[0]!=1)throw new IllegalStateException("Expected one old key listener in "+owner);
        return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args[0]),output=Path.of(args[1]),evidence=Path.of(args[2]);
        Map<String,Object> files=new TreeMap<>();
        try(var jar=new ZipFile(input.toFile())){
            // These annotated methods have no direct callers. Fail rather than
            // leave a stale invokestatic when the input artifact ever changes.
            for(var entry:Collections.list(jar.entries()))if(entry.getName().endsWith(".class")){
                new ClassReader(jar.getInputStream(entry).readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] x){return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitMethodInsn(int op,String o,String n,String d,boolean i){
                            if(KEYS.containsKey(o)&&n.equals("register")&&d.equals(DESC))throw new IllegalStateException("Direct caller needs migration: "+entry.getName());
                        }
                    };}
                },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
            for(String name:List.of(MAIN,"cn/judgfire/BurstClient$Keys","cn/judgfire/CrownInput$Keys")){
                String entry=name+".class";var source=jar.getEntry(entry);
                if(source==null)throw new IllegalStateException("Missing input class "+entry);
                byte[] before=jar.getInputStream(source).readAllBytes();
                byte[] after=name.equals(MAIN)?patchMain(before):patchKeys(name,before);
                Path target=output.resolve(entry);Files.createDirectories(target.getParent());Files.write(target,after);
                files.put(entry,Map.of("before_sha256",hash(before),"after_sha256",hash(after)));
            }
        }
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
            "passed",true,"input",input.toString(),"input_sha256",hash(Files.readAllBytes(input)),"files",files,
            "main_constructor_preserves_configuration_and_module_initialization",true,
            "bootstrap_calls",1,"key_listener_change","method name only; original annotations and body preserved")));
        System.out.println("PASS patched owning constructor and two listener names");
    }
}
