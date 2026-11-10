import java.nio.file.*;import java.util.*;import java.util.jar.*;import java.io.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.*;
public final class PatchFireSurface implements Opcodes {
  static String text(MethodNode m){var t=new Textifier();m.accept(new TraceMethodVisitor(t));var s=new StringWriter();t.print(new PrintWriter(s));return s.toString();}
  public static void main(String[] args)throws Exception {
    try(var jar=new JarFile(args[0])) {
      var reader=new ClassReader(jar.getInputStream(jar.getJarEntry("cn/judgfire/DefaultFlameEffects.class")));var node=new ClassNode();reader.accept(node,0);
      int capture=0,select=0,reload=0;var changed=Set.of("captureBodyOwner","body","reload");var original=new HashMap<String,String>();
      for(var m:node.methods)original.put(m.name+m.desc,text(m));
      for(var m:node.methods) {
        if(m.name.equals("captureBodyOwner")) {
          var prefix=new InsnList();prefix.add(new VarInsnNode(ALOAD,0));prefix.add(new VarInsnNode(ALOAD,1));prefix.add(new VarInsnNode(ILOAD,3));
          prefix.add(new MethodInsnNode(INVOKESTATIC,"cn/judgfire/TierFireSurface","capture","(Lorg/joml/Matrix4f;Lnet/minecraft/world/item/ItemStack;I)V",false));m.instructions.insert(prefix);capture++;
        }
        if(m.name.equals("body"))for(var i:m.instructions.toArray())if(i instanceof FieldInsnNode f&&f.getOpcode()==GETSTATIC&&f.owner.equals(node.name)&&f.name.equals("data")) {
          var suffix=new InsnList();suffix.add(new VarInsnNode(ALOAD,0));suffix.add(new MethodInsnNode(INVOKESTATIC,"cn/judgfire/TierFireSurface","select","(Lcn/judgfire/DefaultFlameEffects$Definition;Lorg/joml/Matrix4f;)Lcn/judgfire/DefaultFlameEffects$Definition;",false));m.instructions.insert(i,suffix);select++;
        }
        if(m.name.equals("reload"))for(var i:m.instructions.toArray())if(i.getOpcode()==RETURN) {
          var prefix=new InsnList();prefix.add(new VarInsnNode(ALOAD,0));prefix.add(new MethodInsnNode(INVOKESTATIC,"cn/judgfire/TierFireSurface","reload","(Lnet/minecraft/server/packs/resources/ResourceManager;)V",false));m.instructions.insertBefore(i,prefix);reload++;
        }
      }
      if(capture!=1||select!=1||reload!=1)throw new AssertionError("Unexpected production connection sites "+capture+" "+select+" "+reload);
      for(var m:node.methods)if(!changed.contains(m.name)&&!original.get(m.name+m.desc).equals(text(m)))throw new AssertionError("Unrelated method changed "+m.name);
      var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);node.accept(writer);var bytes=writer.toByteArray();var check=new ClassNode();new ClassReader(bytes).accept(check,0);
      for(var m:check.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name,m);
      var path=Path.of(args[1]).resolve(node.name+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);
      System.out.println("PASS capture raw gunId + select native definition + reload two resources; all other methods unchanged");
    }
  }
}
