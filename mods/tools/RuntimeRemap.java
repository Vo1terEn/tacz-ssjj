import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Local development remapper; never packaged with the mod or redistributed mappings. */
public class RuntimeRemap {
  static IMappingFile mapping;
  static Map<String, String> globals = new HashMap<>();
  static Map<String, List<String>> hierarchy = new HashMap<>();
  static List<JarFile> jars = new ArrayList<>();

  static String member(String owner, String name, String desc, boolean field, Set<String> seen) {
    if (!seen.add(owner)) return name;
    var c = mapping.getClass(owner);
    if (c != null) {
      String r = field ? c.remapField(name) : c.remapMethod(name, desc);
      if (!r.equals(name)) return r;
    }
    var parents = hierarchy.get(owner);
    if (parents == null) {
      parents = new ArrayList<>();
      try {
        for (var jar : jars) {
          var e = jar.getJarEntry(owner + ".class");
          if (e == null) continue;
          var cr = new ClassReader(jar.getInputStream(e));
          if (cr.getSuperName() != null) parents.add(cr.getSuperName());
          parents.addAll(Arrays.asList(cr.getInterfaces()));
          break;
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      hierarchy.put(owner, parents);
    }
    for (String p : parents) {
      String r = member(p, name, desc, field, seen);
      if (!r.equals(name)) return r;
    }
    return globals.getOrDefault(name, name);
  }

  public static void main(String[] a) throws Exception {
    var srg = IMappingFile.load(new File(a[0]));
    var moj = IMappingFile.load(new File(a[1]));
    mapping = srg.reverse().chain(moj.reverse());
    if (a[2].equals("reverse")) mapping = mapping.reverse();
    else
      for (var c : mapping.getClasses()) {
        for (var f : c.getFields())
          if (f.getOriginal().startsWith("f_")) globals.put(f.getOriginal(), f.getMapped());
        for (var m : c.getMethods())
          if (m.getOriginal().startsWith("m_")) globals.put(m.getOriginal(), m.getMapped());
      }
    for (int i = 3; i < a.length - 1; i++) jars.add(new JarFile(a[i]));
    var in = jars.get(0);
    var dst = Path.of(a[a.length - 1]);
    Files.createDirectories(dst.getParent());
    Remapper r =
        new Remapper() {
          public String map(String n) {
            return mapping.remapClass(n);
          }

          public String mapFieldName(String o, String n, String d) {
            return member(o, n, d, true, new HashSet<>());
          }

          public String mapMethodName(String o, String n, String d) {
            if (n.startsWith("<")) return n;
            return member(o, n, d, false, new HashSet<>());
          }
        };
    int count = 0;
    try (var out = new JarOutputStream(Files.newOutputStream(dst))) {
      for (var it = in.entries(); it.hasMoreElements(); ) {
        var e = it.nextElement();
        String n = e.getName();
        if (e.isDirectory() || n.matches("META-INF/.*\\.(SF|RSA|DSA)")) continue;
        byte[] b = in.getInputStream(e).readAllBytes();
        if (n.endsWith(".class")) {
          var cr = new ClassReader(b);
          var cw = new ClassWriter(0);
          cr.accept(
              new ClassRemapper(cw, r) {
                @Override
                protected MethodVisitor createMethodRemapper(MethodVisitor visitor) {
                  return new MethodRemapper(visitor, r) {
                    @Override
                    public void visitInvokeDynamicInsn(
                        String name, String descriptor, Handle bootstrap, Object... arguments) {
                      // The invokedynamic name is the functional interface's abstract method.
                      // ASM's default Remapper leaves it unchanged because it has no owner.
                      // LambdaMetafactory supplies that owner as the factory return type and
                      // the erased SAM descriptor as its first bootstrap argument.
                      if (bootstrap.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                          && arguments.length > 0
                          && arguments[0] instanceof Type sam
                          && Type.getReturnType(descriptor).getSort() == Type.OBJECT) {
                        name =
                            r.mapMethodName(
                                Type.getReturnType(descriptor).getInternalName(),
                                name,
                                sam.getDescriptor());
                      }
                      super.visitInvokeDynamicInsn(name, descriptor, bootstrap, arguments);
                    }
                  };
                }
              },
              0);
          b = cw.toByteArray();
          n = r.map(n.substring(0, n.length() - 6)) + ".class";
          count++;
        }
        out.putNextEntry(new JarEntry(n));
        out.write(b);
        out.closeEntry();
      }
    }
    for (var j : jars) j.close();
    System.out.println(dst.getFileName() + " classes=" + count);
  }
}
