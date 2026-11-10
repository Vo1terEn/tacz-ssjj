import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchEyeCombat implements Opcodes {
    static final String HELPER="cn/punishmenteye/EyeCombatRules";
    static int innate, speed, gaze, nofaGaze, nofaScale;
    static void require(boolean yes, String message) {if(!yes)throw new IllegalStateException(message);}
    static ClassNode read(JarFile jar,String name)throws Exception {
        var node=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class"))).accept(node,ClassReader.EXPAND_FRAMES);return node;
    }
    static void write(ClassNode node,Path output)throws Exception {
        // Existing frames remain valid: each replacement has the same stack state at all branch labels.
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);
        Path p=output.resolve(node.name+".class");Files.createDirectories(p.getParent());Files.write(p,writer.toByteArray());
    }
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[1]);
        try(var jar=new JarFile(args[0])) {
            var passive=read(jar,"cn/punishmenteye/PassivePower");
            for(var method:passive.methods) {
                if(method.name.equals("hasPower")) {
                    method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();
                    method.instructions.add(new VarInsnNode(ALOAD,0));
                    method.instructions.add(new MethodInsnNode(INVOKESTATIC,HELPER,"isEye","(Lnet/minecraft/world/item/ItemStack;)Z",false));
                    method.instructions.add(new InsnNode(IRETURN));innate++;
                }
                if(method.name.equals("tick")) {
                    // Preserve the 10-tick schedule and all phantom movement/lifecycle code.
                    AbstractInsnNode begin=null;JumpInsnNode schedule=null;
                    for(var ins:method.instructions.toArray()) {
                        if(ins instanceof IntInsnNode n && n.operand==10 && n.getNext().getOpcode()==IREM && n.getNext().getNext() instanceof JumpInsnNode j) {
                            schedule=j;begin=j.getNext();break;
                        }
                    }
                    require(schedule!=null,"Speed schedule missing");
                    AbstractInsnNode cursor=begin;
                    while(cursor!=schedule.label) {var next=cursor.getNext();method.instructions.remove(cursor);cursor=next;}
                    var replacement=new InsnList();replacement.add(new VarInsnNode(ALOAD,1));
                    replacement.add(new MethodInsnNode(INVOKESTATIC,HELPER,"refreshSpeed","(Lnet/minecraft/server/level/ServerPlayer;)V",false));
                    method.instructions.insertBefore(schedule.label,replacement);speed++;
                }
                if(method.name.equals("bonus"))for(var ins:method.instructions.toArray()) {
                    if(ins instanceof MethodInsnNode call && call.owner.equals("cn/punishmenteye/PassiveRules") && call.name.equals("damage")) {
                        method.instructions.insertBefore(call,new VarInsnNode(ALOAD,0));
                        call.owner=HELPER;call.name="gazeDamage";
                        call.desc="(FZLcom/tacz/guns/api/event/common/EntityHurtByGunEvent$Pre;)F";gaze++;
                    }
                }
            }
            write(passive,output);
            var nofa=read(jar,"cn/punishmenteye/NofaPower");
            for(var method:nofa.methods)if(method.name.equals("bullet"))for(var ins:method.instructions.toArray()) {
                if(ins instanceof LdcInsnNode literal && literal.cst instanceof Float v && v==1.5f) {
                    var replacement=new InsnList();replacement.add(new VarInsnNode(ALOAD,3));
                    replacement.add(new MethodInsnNode(INVOKESTATIC,HELPER,"gazeMultiplier","(Lnet/minecraft/world/item/ItemStack;)F",false));
                    method.instructions.insertBefore(ins,replacement);method.instructions.remove(ins);nofaGaze++;
                }
                if(ins instanceof MethodInsnNode call && call.owner.equals("cn/punishmenteye/KingHeartRules") && call.name.equals("lightning")) {
                    var after=new InsnList();after.add(new VarInsnNode(ALOAD,3));
                    after.add(new MethodInsnNode(INVOKESTATIC,HELPER,"scaleLightning","(FLnet/minecraft/world/item/ItemStack;)F",false));
                    method.instructions.insert(ins,after);nofaScale++;
                }
            }
            write(nofa,output);
            var rules=read(jar,"cn/punishmenteye/PassiveRules");
            for(var field:rules.fields)if(field.name.equals("MULTIPLIER"))field.value=1.25f;
            for(var method:rules.methods)if(method.name.equals("damage"))for(var ins:method.instructions.toArray())
                if(ins instanceof LdcInsnNode literal && literal.cst instanceof Float v && v==1.5f)literal.cst=1.25f;
            write(rules,output);
        }
        require(innate==1&&speed==1&&gaze==1&&nofaGaze==1&&nofaScale==1,"Unexpected match counts");
        Files.writeString(output.getParent().resolve("patch_audit.txt"),"Innate ownership="+innate+"\nSpeed="+speed+"\nBullet gaze="+gaze+"\nLightning gaze="+nofaGaze+"\nLightning stage="+nofaScale+"\n");
    }
}
