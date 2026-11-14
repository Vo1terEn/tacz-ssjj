import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/** Independent bytecode linkage audit against the actual installed runtime JARs. */
public final class FreshRuntimeLinks {
    private final FreshRemap.Repository runtime;
    private final Set<String> errors=new TreeSet<>(), referencedClasses=new TreeSet<>(), methods=new TreeSet<>(), fields=new TreeSet<>();
    private String source;
    private FreshRuntimeLinks(FreshRemap.Repository runtime) { this.runtime=runtime; }

    private void classReference(String name) {
        if (name==null) return;
        if (name.startsWith("[")) { type(Type.getType(name)); return; }
        referencedClasses.add(name);
        if (runtime.get(name)==null) errors.add(source+": missing class "+name);
    }
    private void type(Type value) {
        switch(value.getSort()) {
            case Type.OBJECT -> classReference(value.getInternalName());
            case Type.ARRAY -> type(value.getElementType());
            case Type.METHOD -> { type(value.getReturnType()); for (Type argument:value.getArgumentTypes()) type(argument); }
        }
    }
    private FreshRemap.Definition method(String owner,String name,String desc,Set<String> seen) {
        if (owner==null || !seen.add(owner)) return null;
        var definition=runtime.get(owner);
        if (definition==null) return null;
        if (definition.methods.containsKey(name+desc)) return definition;
        if (name.equals("<init>")) return null;
        var found=method(definition.parent,name,desc,seen);
        if (found!=null) return found;
        for (String iface:definition.interfaces) { found=method(iface,name,desc,seen); if (found!=null) return found; }
        return null;
    }
    private FreshRemap.Definition field(String owner,String name,String desc,Set<String> seen) {
        if (owner==null || !seen.add(owner)) return null;
        var definition=runtime.get(owner);
        if (definition==null) return null;
        if (definition.fields.containsKey(name+":"+desc)) return definition;
        for (String iface:definition.interfaces) { var found=field(iface,name,desc,seen); if (found!=null) return found; }
        return field(definition.parent,name,desc,seen);
    }
    private void checkMethod(int opcode,String owner,String name,String desc) {
        classReference(owner); type(Type.getMethodType(desc));
        String key=owner+"."+name+desc; methods.add(key);
        if (owner.startsWith("[") && name.equals("clone")) return;
        var declaration=method(owner,name,desc,new HashSet<>());
        if (declaration==null) {
            if (owner.equals("java/lang/invoke/MethodHandle") && (name.equals("invoke")||name.equals("invokeExact"))) return;
            if (owner.equals("java/lang/invoke/VarHandle")) return;
            errors.add(source+": missing method "+key); return;
        }
        int access=declaration.methods.get(name+desc);
        if (opcode!=0 && ((opcode==Opcodes.INVOKESTATIC)!=((access&Opcodes.ACC_STATIC)!=0)))
            errors.add(source+": static/instance method mismatch "+key);
    }
    private void checkField(int opcode,String owner,String name,String desc) {
        classReference(owner); type(Type.getType(desc));
        String key=owner+"."+name+":"+desc; fields.add(key);
        var declaration=field(owner,name,desc,new HashSet<>());
        if (declaration==null) { errors.add(source+": missing field "+key); return; }
        boolean staticInstruction=opcode==Opcodes.GETSTATIC||opcode==Opcodes.PUTSTATIC;
        if (staticInstruction!=((declaration.fields.get(name+":"+desc)&Opcodes.ACC_STATIC)!=0))
            errors.add(source+": static/instance field mismatch "+key);
    }
    private void constant(Object value) {
        if (value instanceof Type type) type(type);
        if (value instanceof Handle handle) {
            switch(handle.getTag()) {
                case Opcodes.H_GETFIELD -> checkField(Opcodes.GETFIELD,handle.getOwner(),handle.getName(),handle.getDesc());
                case Opcodes.H_GETSTATIC -> checkField(Opcodes.GETSTATIC,handle.getOwner(),handle.getName(),handle.getDesc());
                case Opcodes.H_PUTFIELD -> checkField(Opcodes.PUTFIELD,handle.getOwner(),handle.getName(),handle.getDesc());
                case Opcodes.H_PUTSTATIC -> checkField(Opcodes.PUTSTATIC,handle.getOwner(),handle.getName(),handle.getDesc());
                default -> checkMethod(handle.getTag()==Opcodes.H_INVOKESTATIC?Opcodes.INVOKESTATIC:Opcodes.INVOKEVIRTUAL,
                    handle.getOwner(),handle.getName(),handle.getDesc());
            }
        }
        if (value instanceof ConstantDynamic dynamic) {
            type(Type.getType(dynamic.getDescriptor())); constant(dynamic.getBootstrapMethod());
            for (int i=0;i<dynamic.getBootstrapMethodArgumentCount();i++) constant(dynamic.getBootstrapMethodArgument(i));
        }
    }
    private AnnotationVisitor annotation(String desc) {
        type(Type.getType(desc));
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public void visit(String name,Object value) { constant(value); }
            @Override public void visitEnum(String name,String desc,String value) { checkField(Opcodes.GETSTATIC,Type.getType(desc).getInternalName(),value,desc); }
            @Override public AnnotationVisitor visitAnnotation(String name,String desc) { return annotation(desc); }
            @Override public AnnotationVisitor visitArray(String name) { return this; }
        };
    }
    private void scan(byte[] bytes) {
        source=new ClassReader(bytes).getClassName();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(int version,int access,String name,String signature,String parent,String[] interfaces) {
                classReference(parent); for (String iface:interfaces) classReference(iface);
            }
            @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible) { return annotation(desc); }
            @Override public FieldVisitor visitField(int access,String name,String desc,String signature,Object value) {
                type(Type.getType(desc)); constant(value);
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible) { return annotation(desc); }
                };
            }
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                type(Type.getMethodType(desc)); if (exceptions!=null) for (String exception:exceptions) classReference(exception);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible) { return annotation(desc); }
                    @Override public AnnotationVisitor visitParameterAnnotation(int index,String desc,boolean visible) { return annotation(desc); }
                    @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean iface) { checkMethod(opcode,owner,name,desc); }
                    @Override public void visitFieldInsn(int opcode,String owner,String name,String desc) { checkField(opcode,owner,name,desc); }
                    @Override public void visitTypeInsn(int opcode,String type) { classReference(type); }
                    @Override public void visitLdcInsn(Object value) { constant(value); }
                    @Override public void visitMultiANewArrayInsn(String desc,int dimensions) { type(Type.getType(desc)); }
                    @Override public void visitTryCatchBlock(Label start,Label end,Label handler,String type) { classReference(type); }
                    @Override public void visitInvokeDynamicInsn(String name,String desc,Handle bootstrap,Object... arguments) {
                        type(Type.getMethodType(desc)); constant(bootstrap); for (Object value:arguments) constant(value);
                        if (bootstrap.getOwner().equals("java/lang/invoke/LambdaMetafactory") && arguments.length>0 && arguments[0] instanceof Type sam) {
                            Type iface=Type.getReturnType(desc);
                            if (iface.getSort()==Type.OBJECT) checkMethod(Opcodes.INVOKEINTERFACE,iface.getInternalName(),name,sam.getDescriptor());
                        }
                    }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=3) throw new IllegalArgumentException("new-runtime.jar actual_runtime_cp.txt report.json");
        List<Path> paths=new ArrayList<>(); paths.add(Path.of(args[0])); paths.addAll(FreshRemap.readPaths(Path.of(args[1])));
        try (var repository=new FreshRemap.Repository(paths); var jar=new JarFile(args[0])) {
            FreshRuntimeLinks check=new FreshRuntimeLinks(repository); int count=0;
            for (var entry:Collections.list(jar.entries())) if (entry.getName().endsWith(".class")) {
                try (var input=jar.getInputStream(entry)) { check.scan(input.readAllBytes()); count++; }
            }
            FreshRemap.report(Path.of(args[2]),Map.of("passed",check.errors.isEmpty(),"scanned_classes",count,
                "referenced_classes",check.referencedClasses.size(),"referenced_methods",check.methods.size(),
                "referenced_fields",check.fields.size(),"errors",check.errors,"classpath",paths.stream().map(Path::toString).toList(),
                "method_references",check.methods,"field_references",check.fields));
            System.out.println("FreshRuntimeLinks classes="+count+" methods="+check.methods.size()+" fields="+check.fields.size()+" errors="+check.errors.size());
            if (!check.errors.isEmpty()) throw new IllegalStateException(String.join("\n",check.errors));
        }
    }
}
