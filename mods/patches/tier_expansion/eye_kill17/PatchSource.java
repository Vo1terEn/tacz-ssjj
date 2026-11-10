import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep the original factory and all damage instructions; decorate only its successful return. */
public final class PatchSource {
  public static void main(String[] a) throws Exception {
    ClassNode n = new ClassNode();
    try (var jar = new JarFile(a[0])) {
      new ClassReader(jar.getInputStream(jar.getJarEntry("cn/punishmenteye/NofaPower.class")))
          .accept(n, ClassReader.EXPAND_FRAMES);
    }
    int methods = 0, returns = 0;
    for (MethodNode m : n.methods) if (m.name.equals("source") && m.desc.equals(
        "(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)Lnet/minecraft/world/damagesource/DamageSource;")) {
      methods++;
      for (AbstractInsnNode i : m.instructions.toArray()) if (i.getOpcode() == Opcodes.ARETURN) {
        InsnList extra = new InsnList();
        extra.add(new VarInsnNode(Opcodes.ALOAD, 1));
        extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"cn/punishmenteye/EyeSkillDamage","wrap",
            "(Lnet/minecraft/world/damagesource/DamageSource;Ljava/lang/String;)Lnet/minecraft/world/damagesource/DamageSource;",false));
        m.instructions.insertBefore(i,extra);returns++;
      }
    }
    if (methods != 1 || returns != 1) throw new AssertionError("factory anchors "+methods+"/"+returns);
    ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);
    Path p=Path.of(a[1],n.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());
    System.out.println("Patched exactly one source factory return; all existing source construction and damage calls retained.");
  }
}
