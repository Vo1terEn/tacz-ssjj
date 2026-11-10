import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Preserve shipping lifecycle, saved state and weak identity caches verbatim. */
public final class OverloadPatch {
    static final String NAME="cn/ssjjdimension/DimensionOverload", HELPER="cn/ssjjdimension/OverloadDamage";
    static final Set<String> CHANGED=Set.of("refresh","projectile","gun","trident");
    static ClassNode node(byte[] bytes){var n=new ClassNode();new ClassReader(bytes).accept(n,0);return n;}
    static byte[] outside(byte[] bytes){var n=node(bytes);n.methods.removeIf(m->CHANGED.contains(m.name));var w=new ClassWriter(0);n.accept(w);return w.toByteArray();}
    static AbstractInsnNode prev(AbstractInsnNode n){do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
    static void reset(MethodNode m){m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;m.visibleLocalVariableAnnotations=null;m.invisibleLocalVariableAnnotations=null;}
    public static void main(String[] args)throws Exception {
        byte[] original;
        try(var jar=new JarFile(args[0]);var in=jar.getInputStream(jar.getJarEntry(NAME+".class"))){original=in.readAllBytes();}
        var n=node(original);var changed=new ArrayList<String>();
        for(var m:n.methods) {
            if(!CHANGED.contains(m.name))continue;
            if(m.name.equals("projectile")) {
                TypeInsnNode first=null;
                for(var x:m.instructions)if(x instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.INSTANCEOF&&type.desc.equals("com/tacz/guns/entity/EntityKineticBullet")){first=type;break;}
                if(first==null)throw new AssertionError("Missing old projectile branch");
                var get=prev(first);
                while(get!=null&&!(get instanceof MethodInsnNode call&&call.owner.equals("net/minecraftforge/event/entity/EntityJoinLevelEvent")&&call.desc.equals("()Lnet/minecraft/world/entity/Entity;")))get=prev(get);
                if(get==null)throw new AssertionError("Missing projectile event entity load");
                var load=prev(get);
                if(!(get instanceof MethodInsnNode call)||!call.desc.equals("()Lnet/minecraft/world/entity/Entity;")
                        ||!(load instanceof VarInsnNode v)||v.getOpcode()!=Opcodes.ALOAD||v.var!=0)throw new AssertionError("Unexpected projectile suffix");
                // Preserve the entire living-entity save restoration prefix and its labels.
                while(load!=null){var next=load.getNext();m.instructions.remove(load);load=next;}
                m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
                m.instructions.add(new MethodInsnNode(call.getOpcode(),call.owner,call.name,call.desc,call.itf));
                m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"launched","(Lnet/minecraft/world/entity/Entity;)V",false));
                m.instructions.add(new InsnNode(Opcodes.RETURN));
                m.localVariables=null;m.visibleLocalVariableAnnotations=null;m.invisibleLocalVariableAnnotations=null;
            } else {
                reset(m);
                if(m.name.equals("refresh")) {
                    if(!m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;D)V"))throw new AssertionError(m.desc);
                    m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
                    m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"clearAttribute","(Lnet/minecraft/world/entity/LivingEntity;)V",false));
                } else if(m.name.equals("gun")) {
                    m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
                    m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"gun",m.desc,false));
                }
                m.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            changed.add(m.name+m.desc);
        }
        if(changed.size()!=4)throw new AssertionError(changed);
        for(var method:n.methods)if((method.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))==0)new Analyzer<>(new BasicVerifier()).analyze(n.name,method);
        var writer=new ClassWriter(0);n.accept(writer);byte[] output=writer.toByteArray();
        if(!Arrays.equals(outside(original),outside(output)))throw new AssertionError("Unrelated lifecycle/cache/metadata changed");
        var target=Path.of(args[1]).resolve(NAME+".class");Files.createDirectories(target.getParent());Files.write(target,output);
        byte[] weapon;
        String weaponName="cn/judgfire/WeaponDamage";
        try(var jar=new JarFile(args[0]);var in=jar.getInputStream(jar.getJarEntry(weaponName+".class"))){weapon=in.readAllBytes();}
        var facade=node(weapon);int forwards=0;
        for(var method:facade.methods)if(method.name.equals("<init>")&&method.desc.startsWith("(Lnet/minecraft/world/damagesource/DamageSource;")) {
            for(var instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN) {
                var copy=new InsnList();copy.add(new VarInsnNode(Opcodes.ALOAD,1));copy.add(new VarInsnNode(Opcodes.ALOAD,0));
                copy.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"forward","(Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/damagesource/DamageSource;)V",false));
                method.instructions.insertBefore(instruction,copy);forwards++;
            }
        }
        if(forwards!=1)throw new AssertionError("Unexpected weapon facade constructor count: "+forwards);
        for(var method:facade.methods)if((method.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))==0)new Analyzer<>(new BasicVerifier()).analyze(facade.name,method);
        var facadeWriter=new ClassWriter(0);facade.accept(facadeWriter);byte[] wrapped=facadeWriter.toByteArray();
        // Remove only the inserted transfer and require the complete original class.
        var audit=node(wrapped);
        for(var method:audit.methods)for(var instruction:method.instructions.toArray())
            if(instruction instanceof MethodInsnNode call&&call.owner.equals(HELPER)&&call.name.equals("forward")) {
                var receiver=prev(call);var originalSource=prev(receiver);
                method.instructions.remove(originalSource);method.instructions.remove(receiver);method.instructions.remove(call);
            }
        var cleanWriter=new ClassWriter(0);audit.accept(cleanWriter);var oldWriter=new ClassWriter(0);node(weapon).accept(oldWriter);
        if(!Arrays.equals(cleanWriter.toByteArray(),oldWriter.toByteArray()))throw new AssertionError("Facade changed outside snapshot transfer");
        var facadeTarget=Path.of(args[1]).resolve(weaponName+".class");Files.createDirectories(facadeTarget.getParent());Files.write(facadeTarget,wrapped);
        FreshRemap.report(Path.of(args[2]),Map.of("passed",true,"changed_methods",changed,
                "other_methods_fields_and_metadata_identical",true,"identity_cache_clinit_preserved",true,
                "weapon_facade_snapshot_transfer",true,"weapon_facade_other_instructions_preserved",true,
                "preserved_projectile_prefix","Original saved living-state restoration, including weapon recovery and network sync"));
        System.out.println("PASS four overload handlers consolidated; lifecycle and identity caches unchanged");
    }
}
