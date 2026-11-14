import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipFile;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** New named-to-SRG translator. Inputs are official mappings and generic APIs. */
public final class FreshRemap {
    static final class Definition {
        String name, parent;
        String[] interfaces;
        int access;
        final Map<String,Integer> methods=new HashMap<>(), fields=new HashMap<>();
    }

    /** Reads bytecode metadata without loading or initializing game classes. */
    static final class Repository implements AutoCloseable {
        final List<Path> roots=new ArrayList<>();
        final Map<Path,ZipFile> archives=new HashMap<>();
        final Map<String,Definition> cache=new HashMap<>();
        final Set<String> absent=new HashSet<>();
        Repository(Collection<Path> paths) throws IOException {
            for (Path path:paths) {
                Path absolute=path.toAbsolutePath().normalize();
                if (!Files.exists(absolute)) throw new FileNotFoundException(absolute.toString());
                roots.add(absolute);
                if (!Files.isDirectory(absolute)) archives.put(absolute,new ZipFile(absolute.toFile()));
            }
        }
        byte[] bytes(String internalName) throws IOException {
            String entry=internalName+".class";
            for (Path root:roots) {
                if (Files.isDirectory(root)) {
                    Path file=root.resolve(entry);
                    if (Files.isRegularFile(file)) return Files.readAllBytes(file);
                } else {
                    ZipFile zip=archives.get(root);
                    var found=zip.getEntry(entry);
                    if (found!=null) try (InputStream input=zip.getInputStream(found)) { return input.readAllBytes(); }
                }
            }
            // JDK modules are only a metadata fallback, never Minecraft APIs.
            if (internalName.startsWith("java/") || internalName.startsWith("javax/")
                    || internalName.startsWith("jdk/") || internalName.startsWith("sun/")) {
                try (InputStream input=ClassLoader.getSystemResourceAsStream(entry)) {
                    if (input!=null) return input.readAllBytes();
                }
            }
            return null;
        }
        Definition get(String name) {
            if (name==null || absent.contains(name)) return null;
            if (cache.containsKey(name)) return cache.get(name);
            try {
                byte[] bytes=bytes(name);
                if (bytes==null) { absent.add(name); return null; }
                Definition result=new Definition();
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public void visit(int version,int access,String name,String signature,String parent,String[] interfaces) {
                        result.name=name; result.parent=parent; result.interfaces=interfaces; result.access=access;
                    }
                    @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                        result.methods.put(name+desc,access); return null;
                    }
                    @Override public FieldVisitor visitField(int access,String name,String desc,String signature,Object value) {
                        result.fields.put(name+":"+desc,access); return null;
                    }
                },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                cache.put(name,result);
                return result;
            } catch (IOException error) { throw new UncheckedIOException(error); }
        }
        @Override public void close() throws IOException { for (ZipFile zip:archives.values()) zip.close(); }
    }

    static List<Path> readPaths(Path file) throws IOException {
        List<Path> result=new ArrayList<>();
        for (String line:Files.readAllLines(file,StandardCharsets.UTF_8)) {
            if (!line.isBlank()) result.add(Path.of(line.strip()));
        }
        return result;
    }
    static void report(Path path,Object value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(value)+"\n");
    }

    static final class InheritedNames extends Remapper {
        final IMappingFile mapping;
        final Repository named;
        final Set<String> changedMethods=new TreeSet<>(), changedFields=new TreeSet<>(), changedClasses=new TreeSet<>();
        InheritedNames(IMappingFile mapping,Repository named) { this.mapping=mapping; this.named=named; }
        @Override public String map(String name) {
            String mapped=mapping.remapClass(name);
            if (!name.equals(mapped)) changedClasses.add(name+" -> "+mapped);
            return mapped;
        }
        String method(String owner,String name,String desc,Set<String> seen) {
            if (owner==null || !seen.add(owner)) return null;
            IMappingFile.IClass table=mapping.getClass(owner);
            if (table!=null && table.getMethod(name,desc)!=null) return table.remapMethod(name,desc);
            Definition definition=named.get(owner);
            if (definition==null) return null;
            Integer declared=definition.methods.get(name+desc);
            if (declared!=null && (declared&(Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE))!=0) return name;
            String found=method(definition.parent,name,desc,seen);
            if (found!=null) return found;
            for (String iface:definition.interfaces) {
                found=method(iface,name,desc,seen);
                if (found!=null) return found;
            }
            return null;
        }
        @Override public String mapMethodName(String owner,String name,String desc) {
            if (name.startsWith("<")) return name;
            String found=method(owner,name,desc,new HashSet<>());
            if (found==null) return name;
            if (!found.equals(name)) changedMethods.add(owner+"."+name+desc+" -> "+found);
            return found;
        }
        String field(String owner,String name,String desc,Set<String> seen) {
            if (owner==null || !seen.add(owner)) return null;
            IMappingFile.IClass table=mapping.getClass(owner);
            if (table!=null && table.getField(name)!=null) return table.remapField(name);
            Definition definition=named.get(owner);
            if (definition==null) return null;
            if (definition.fields.containsKey(name+":"+desc)) return name;
            for (String iface:definition.interfaces) {
                String found=field(iface,name,desc,seen);
                if (found!=null) return found;
            }
            return field(definition.parent,name,desc,seen);
        }
        @Override public String mapFieldName(String owner,String name,String desc) {
            String found=field(owner,name,desc,new HashSet<>());
            if (found==null) return name;
            if (!found.equals(name)) changedFields.add(owner+"."+name+":"+desc+" -> "+found);
            return found;
        }
    }

    static byte[] translate(byte[] input,InheritedNames names) {
        ClassWriter writer=new ClassWriter(0);
        new ClassReader(input).accept(new ClassRemapper(writer,names) {
            @Override protected MethodVisitor createMethodRemapper(MethodVisitor target) {
                return new MethodRemapper(Opcodes.ASM9,target,names) {
                    @Override public void visitInvokeDynamicInsn(String name,String desc,Handle bootstrap,Object... arguments) {
                        if (bootstrap.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                                && arguments.length>0 && arguments[0] instanceof Type sam) {
                            Type iface=Type.getReturnType(desc);
                            if (iface.getSort()==Type.OBJECT) name=names.mapMethodName(iface.getInternalName(),name,sam.getDescriptor());
                        }
                        super.visitInvokeDynamicInsn(name,desc,bootstrap,arguments);
                    }
                };
            }
        },0);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length!=6) throw new IllegalArgumentException("official.map merged.tsrg named_dir output.jar named_cp.txt report.json");
        IMappingFile chain=IMappingFile.load(new File(args[0])).chain(IMappingFile.load(new File(args[1])));
        Path input=Path.of(args[2]), output=Path.of(args[3]);
        List<Path> paths=new ArrayList<>(); paths.add(input); paths.addAll(readPaths(Path.of(args[4])));
        int classes=0;
        try (Repository repository=new Repository(paths)) {
            InheritedNames names=new InheritedNames(chain,repository);
            Files.createDirectories(output.toAbsolutePath().getParent());
            try (JarOutputStream jar=new JarOutputStream(Files.newOutputStream(output)); var files=Files.walk(input)) {
                for (Path file:files.filter(p->p.toString().endsWith(".class")).sorted().toList()) {
                    byte[] translated=translate(Files.readAllBytes(file),names);
                    String entry=new ClassReader(translated).getClassName()+".class";
                    JarEntry item=new JarEntry(entry); item.setTime(0L); jar.putNextEntry(item); jar.write(translated); jar.closeEntry(); classes++;
                }
            }
            report(Path.of(args[5]),Map.of("classes",classes,"class_changes",names.changedClasses,
                "method_changes",names.changedMethods,"field_changes",names.changedFields,
                "mapping_chain","official named->obf, then local merged tsrg2 obf->SRG",
                "lambda_sam_names_remapped",true,"hierarchy_declarations_resolved",true));
        }
        System.out.println("FreshRemap classes="+classes+" output="+output);
    }
}
