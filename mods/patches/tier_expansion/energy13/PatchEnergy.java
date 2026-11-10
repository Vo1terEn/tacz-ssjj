import java.nio.file.*;import java.util.*;import java.util.jar.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.util.*;
public final class PatchEnergy {
 static String code(MethodNode m){var t=new Textifier();m.accept(new TraceMethodVisitor(t));var w=new java.io.StringWriter();t.print(new java.io.PrintWriter(w));return w.toString();}
 static ClassNode node(byte[] raw){var n=new ClassNode();new ClassReader(raw).accept(n,ClassReader.SKIP_DEBUG);return n;}
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[2]);try(var reference=new JarFile(args[0]);var baseline=new JarFile(args[1])){
   String name="cn/judgfire/EnergyScaling.class";byte[] raw=reference.getInputStream(reference.getJarEntry(name)).readAllBytes();var n=new ClassNode();var reader=new ClassReader(raw);reader.accept(n,0);int changed=0;
   for(var a:n.visibleAnnotations)if(a.desc.equals("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;"))for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals("modid")){if(!a.values.get(i+1).equals("judgfire_runtime"))throw new AssertionError("Unexpected reference owner");a.values.set(i+1,"eye_judgment_arsenal");changed++;}
   if(changed!=1)throw new AssertionError("Expected one subscriber modid");var w=new ClassWriter(reader,0);n.accept(w);byte[] patched=w.toByteArray();var left=node(raw);var right=node(patched);if(left.methods.size()!=right.methods.size())throw new AssertionError("Methods changed");for(int i=0;i<left.methods.size();i++)if(!code(left.methods.get(i)).equals(code(right.methods.get(i))))throw new AssertionError("Reference method changed "+left.methods.get(i).name);
   var old=node(baseline.getInputStream(baseline.getJarEntry(name)).readAllBytes());var original=old.methods.stream().filter(m->m.name.equals("multiplier")).findFirst().orElseThrow();var updated=right.methods.stream().filter(m->m.name.equals("multiplier")).findFirst().orElseThrow();if(!code(original).equals(code(updated)))throw new AssertionError("Energy curve changed");
   Path dest=out.resolve(name);Files.createDirectories(dest.getParent());Files.write(dest,patched);System.out.println("PASS subscriber annotation only; all reference methods and current multiplier bytecode unchanged");
  }
 }
}
