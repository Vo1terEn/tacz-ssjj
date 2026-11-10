import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep the exact shipping DimensionField bytecode except its two potion applications. */
public final class StunCallsitePatch {
    public static void main(String[] args) throws Exception {
        Path baseline=Path.of(args[0]), overlay=Path.of(args[1]), report=Path.of(args[2]);
        String name="cn/ssjjdimension/DimensionField.class";
        byte[] bytes;
        try(var jar=new JarFile(baseline.toFile());var in=jar.getInputStream(jar.getJarEntry(name))) { bytes=in.readAllBytes(); }
        var node=new ClassNode();new ClassReader(bytes).accept(node,0);
        List<String> changes=new ArrayList<>();
        for(var method:node.methods) {
            for(var instruction=method.instructions.getFirst();instruction!=null;) {
                var next=instruction.getNext();
                if(instruction instanceof TypeInsnNode allocation && allocation.getOpcode()==Opcodes.NEW && allocation.desc.equals("net/minecraft/world/effect/MobEffectInstance")) {
                    var duplicate=allocation.getNext();var field=duplicate.getNext();
                    if(!(field instanceof FieldInsnNode f) || !f.owner.equals("cn/judgfire/BurstStun") || !f.name.equals("STUN")) { instruction=next;continue; }
                    var get=field.getNext();var cast=get.getNext();
                    if(duplicate.getOpcode()!=Opcodes.DUP || !(cast instanceof TypeInsnNode type) || !type.desc.equals("net/minecraft/world/effect/MobEffect"))throw new IllegalStateException("Unexpected effect prefix");
                    var constructor=cast.getNext();
                    while(constructor!=null && !(constructor instanceof MethodInsnNode m && m.getOpcode()==Opcodes.INVOKESPECIAL && m.owner.equals("net/minecraft/world/effect/MobEffectInstance") && m.name.equals("<init>")))constructor=constructor.getNext();
                    if(constructor==null)throw new IllegalStateException("Missing effect constructor");
                    var application=constructor.getNext();
                    if(!(application instanceof MethodInsnNode apply) || !apply.desc.equals("(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))throw new IllegalStateException("Unexpected application");
                    // Drop the old amplifier, ambient, particles and icon arguments; leave duration.
                    var flag=constructor.getPrevious();
                    for(int n=0;n<4;n++) { var previous=flag.getPrevious();if(flag.getOpcode()!=(n==0?Opcodes.ICONST_1:Opcodes.ICONST_0))throw new IllegalStateException("Unexpected flags");method.instructions.remove(flag);flag=previous; }
                    for(var old:List.of(allocation,duplicate,field,get,cast,constructor))method.instructions.remove(old);
                    method.instructions.set(application,new MethodInsnNode(Opcodes.INVOKESTATIC,"cn/judgfire/BurstStun","refresh","(Lnet/minecraft/world/entity/LivingEntity;I)Z",false));
                    changes.add(method.name+method.desc);next=application.getNext();
                    // application was replaced; resume after the replacement via the preserved POP.
                    next=method.instructions.getFirst();
                }
                instruction=next;
            }
        }
        if(changes.size()!=2)throw new IllegalStateException("Expected 2 DimensionField applications, got "+changes);
        var writer=new ClassWriter(0);node.accept(writer);
        var target=overlay.resolve(name);Files.createDirectories(target.getParent());Files.write(target,writer.toByteArray());
        FreshRemap.report(report,Map.of("passed",true,"class",name,"patched_calls",changes,"preserved","all other original shipping instructions, including CombatMessages hooks"));
        System.out.println("PASS two exact DimensionField potion applications -> independent refresh");
    }
}
