import java.nio.file.*;import java.util.*;import java.util.jar.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;
public final class PatchAmmo {
 public static void main(String[] a)throws Exception{
  ClassNode n=new ClassNode();try(var j=new JarFile(a[0])){new ClassReader(j.getInputStream(j.getJarEntry("cn/judgfire/EmperorCrown.class"))).accept(n,ClassReader.EXPAND_FRAMES);}
  int changes=0;for(MethodNode m:n.methods)if(m.name.equals("tick")){
   int accepted=m.maxLocals++;for(AbstractInsnNode f:m.instructions.toArray())if(f instanceof FrameNode frame){int slots=0;for(Object t:frame.local)slots+=(t.equals(Opcodes.LONG)||t.equals(Opcodes.DOUBLE))?2:1;while(slots++<accepted)frame.local.add(Opcodes.TOP);frame.local.add(Opcodes.INTEGER);}var init=new InsnList();init.add(new InsnNode(Opcodes.ICONST_0));init.add(new VarInsnNode(Opcodes.ISTORE,accepted));m.instructions.insert(init);
   int hurt=0,refund=0;for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode c){
    if(c.owner.equals("net/minecraft/world/entity/LivingEntity")&&c.name.equals("m_6469_")&&c.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")){
     var save=new InsnList();save.add(new InsnNode(Opcodes.DUP));save.add(new VarInsnNode(Opcodes.ISTORE,accepted));m.instructions.insert(c,save);hurt++;
    }
    if(c.owner.equals("cn/judgfire/JudgmentAmmo")&&c.name.equals("autoHit")){
     var receipt=new InsnList();receipt.add(new VarInsnNode(Opcodes.ILOAD,accepted));receipt.add(new VarInsnNode(Opcodes.ALOAD,17));receipt.add(new FieldInsnNode(Opcodes.GETFIELD,"cn/judgfire/EmperorCrown$CrownImpact","charged","Z"));receipt.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"cn/judgfire/CrownAmmoReceipt","confirmed","(DZZ)D",false));m.instructions.insertBefore(c,receipt);refund++;
    }
   }if(hurt!=1||refund!=1)throw new AssertionError("expected exact current anchors "+hurt+"/"+refund);changes++;
  }if(changes!=1)throw new AssertionError("tick count "+changes);
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);byte[] out=w.toByteArray();new ClassReader(out).accept(new ClassVisitor(Opcodes.ASM9){public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] ex){return null;}},0);
  Path p=Path.of(a[1],n.name+".class");Files.createDirectories(p.getParent());Files.write(p,out);System.out.println("Patched only EmperorCrown.tick: capture hurt acceptance and confirm refund receipt, original loss untouched");
 }
}
