import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.google.gson.GsonBuilder;

/** Exactly two source-specific additions; all original executable instructions remain in order. */
public final class PatchDestructionEnergy {
    static final String DAMAGE="cn/ssjjparasite2/client/ParasiteDestructionDamage";
    static final String EVENT="net/minecraftforge/event/entity/living/LivingDamageEvent";
    static final String LIVING="Lnet/minecraft/world/entity/LivingEntity;",SOURCE="Lnet/minecraft/world/damagesource/DamageSource;";
    static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static ClassNode node(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,ClassReader.EXPAND_FRAMES);return n;}
    static List<String> instructions(MethodNode m){
        var out=new ArrayList<String>();var labels=new IdentityHashMap<LabelNode,Integer>();int index=0;
        for(var i:m.instructions)if(i.getOpcode()>=0){if(i instanceof LabelNode l)labels.put(l,index);index++;}
        for(var i:m.instructions){if(i.getOpcode()<0)continue;String s=""+i.getOpcode();
            if(i instanceof VarInsnNode v)s+=":"+v.var;
            else if(i instanceof MethodInsnNode v)s+=":"+v.owner+":"+v.name+":"+v.desc+":"+v.itf;
            else if(i instanceof FieldInsnNode v)s+=":"+v.owner+":"+v.name+":"+v.desc;
            else if(i instanceof TypeInsnNode v)s+=":"+v.desc;
            else if(i instanceof LdcInsnNode v)s+=":"+v.cst;
            else if(i instanceof IntInsnNode v)s+=":"+v.operand;
            else if(i instanceof IincInsnNode v)s+=":"+v.var+":"+v.incr;
            else if(i instanceof InvokeDynamicInsnNode v)s+=":"+v.name+":"+v.desc+":"+v.bsm+":"+Arrays.toString(v.bsmArgs);
            else if(i instanceof MultiANewArrayInsnNode v)s+=":"+v.desc+":"+v.dims;
            // Original jump nodes themselves are preserved, including their exact LabelNode identity.
            out.add(s);
        }return out;
    }
    static MethodNode method(ClassNode n,String name,String desc){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
    static byte[] patch(byte[] input,boolean energy){
        var n=node(input);String name=energy?"damage":"enter";
        String desc=energy?"(L"+EVENT+";)V":"("+LIVING+SOURCE+"Z)V";
        var target=method(n,name,desc);var old=target.instructions.toArray();
        var originalMethods=new LinkedHashMap<String,List<String>>();for(var m:n.methods)originalMethods.put(m.name+m.desc,instructions(m));
        if(Arrays.stream(old).anyMatch(i->i instanceof TypeInsnNode t&&t.desc.equals(DAMAGE)))throw new IllegalStateException("Input is already patched");
        if(energy){
            String getter=Arrays.stream(old).filter(i->i instanceof MethodInsnNode m&&m.owner.equals(EVENT)&&m.desc.equals("()"+SOURCE)).map(i->((MethodInsnNode)i).name).distinct().reduce((a,b)->{throw new IllegalStateException("Ambiguous damage-source getter");}).orElseThrow();
            var code=new InsnList();var proceed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,0));
            code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,EVENT,getter,"()"+SOURCE,false));
            code.add(new TypeInsnNode(Opcodes.INSTANCEOF,DAMAGE));code.add(new JumpInsnNode(Opcodes.IFEQ,proceed));code.add(new InsnNode(Opcodes.RETURN));code.add(proceed);
            code.add(new FrameNode(Opcodes.F_NEW,1,new Object[]{EVENT},0,new Object[]{}));target.instructions.insert(code);
        }else{
            var calls=Arrays.stream(old).filter(i->i instanceof MethodInsnNode m&&m.getOpcode()==Opcodes.INVOKESPECIAL&&m.owner.equals("cn/judgfire/BarrierHealth$Hit")&&m.name.equals("<init>")&&m.desc.equals("(Ljava/lang/ref/WeakReference;Ljava/lang/ref/WeakReference;DDD)V")).toList();
            if(calls.size()!=1)throw new IllegalStateException("Unexpected native Hit constructor");
            var ctor=(MethodInsnNode)calls.get(0);var a=ctor.getPrevious();var b=a.getPrevious();var c=b.getPrevious();
            if(!(a instanceof VarInsnNode strength&&a.getOpcode()==Opcodes.DLOAD&&b instanceof VarInsnNode resist&&b.getOpcode()==Opcodes.DLOAD&&c instanceof VarInsnNode penetration&&c.getOpcode()==Opcodes.DLOAD))throw new IllegalStateException("Native Hit energy operands changed");
            var code=new InsnList();var proceed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new TypeInsnNode(Opcodes.INSTANCEOF,DAMAGE));code.add(new JumpInsnNode(Opcodes.IFEQ,proceed));
            code.add(new InsnNode(Opcodes.DCONST_0));code.add(new VarInsnNode(Opcodes.DSTORE,penetration.var));code.add(new InsnNode(Opcodes.DCONST_0));code.add(new VarInsnNode(Opcodes.DSTORE,resist.var));code.add(new InsnNode(Opcodes.DCONST_1));code.add(new VarInsnNode(Opcodes.DSTORE,strength.var));code.add(proceed);
            // The uninitialized Hit and both WeakReferences are already on the stack.
            // Insert before allocation instead, where locals are initialized and stack empty.
            AbstractInsnNode begin=c;while(begin!=null&&!(begin instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==3))begin=begin.getPrevious();
            if(begin==null)throw new IllegalStateException("Missing native stack push anchor");
            code.add(new FrameNode(Opcodes.F_NEW,8,new Object[]{"net/minecraft/world/entity/LivingEntity","net/minecraft/world/damagesource/DamageSource",Opcodes.INTEGER,"java/util/ArrayDeque","cn/judgfire/BarrierHealth$Hit",Opcodes.DOUBLE,Opcodes.DOUBLE,Opcodes.DOUBLE},0,new Object[]{}));
            target.instructions.insertBefore(begin,code);
        }
        // Exact identity retention proves branches, labels, try/finally and original opcodes survive.
        int cursor=0;for(var i:target.instructions)if(cursor<old.length&&i==old[cursor])cursor++;
        if(cursor!=old.length)throw new AssertionError("Original instructions were modified");
        for(var m:n.methods)if(m!=target&&!instructions(m).equals(originalMethods.get(m.name+m.desc)))throw new AssertionError("Unrelated method changed");
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(writer);return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args[0]),out=Path.of(args[1]),report=Path.of(args[2]);var files=new TreeMap<String,Object>();String beforeEnergy=null,afterEnergy=null;
        try(var zip=new ZipFile(input.toFile())){
            for(String name:List.of("EnergyScaling","BarrierHealth")){
                String entry="cn/judgfire/"+name+".class";byte[] before=zip.getInputStream(zip.getEntry(entry)).readAllBytes(),after=patch(before,name.equals("EnergyScaling"));
                Path path=out.resolve(entry);Files.createDirectories(path.getParent());Files.write(path,after);files.put(entry,Map.of("before_sha256",hash(before),"after_sha256",hash(after),"original_instructions_preserved",true));
                if(name.equals("EnergyScaling")){beforeEnergy=hash(before);afterEnergy=hash(after);}
            }
        }
        var data=new LinkedHashMap<String,Object>();data.put("passed",true);data.put("input_sha256",hash(Files.readAllBytes(input)));data.put("before_sha256",beforeEnergy);data.put("after_sha256",afterEnergy);data.put("files",files);data.put("only_damage_source_guard_added",true);data.put("original_instructions_preserved",true);data.put("source_specific_neutral_hit",true);data.put("unrelated_source_and_nested_scopes_preserved",true);
        Files.createDirectories(report.getParent());Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(data));System.out.println("PASS source-specific destruction energy guards; native instructions preserved");
    }
}
