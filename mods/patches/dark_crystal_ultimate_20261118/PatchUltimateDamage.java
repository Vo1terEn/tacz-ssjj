import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/** Wrap only the installed creative settlement. The original guarded method is
 * retained under a private name, including its original code/frames/handlers. */
public final class PatchUltimateDamage implements Opcodes {
    static final String ENTRY="cn/fusionarsenal/CreativeDamage.class";
    static final String OWNER="cn/fusionarsenal/CreativeDamage";
    static final String ORIGINAL="applyDarkUltimateOriginal";
    static final String ENTITY="Lnet/minecraft/world/entity/LivingEntity;";
    static final String SOURCE="Lnet/minecraft/world/damagesource/DamageSource;";
    static final String DESC="("+ENTITY+SOURCE+"FD)Z";
    static final String SCOPE="cn/ssjjdark/DarkLiberationBypass$Scope";
    static byte[] patch(byte[] baseline){
        var reader=new ClassReader(baseline);
        var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        int[] found={0};
        var visitor=new ClassVisitor(ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                if(name.equals(ORIGINAL))throw new IllegalStateException("Already wrapped creative settlement");
                if(name.equals("apply")&&descriptor.equals(DESC)){
                    if(access!=(ACC_PRIVATE|ACC_STATIC))throw new IllegalStateException("Unexpected apply access "+access);
                    found[0]++;
                    // Returning the MethodWriter directly keeps the baseline Code attribute verbatim.
                    return super.visitMethod(access,ORIGINAL,descriptor,signature,exceptions);
                }
                return super.visitMethod(access,name,descriptor,signature,exceptions);
            }
            @Override public void visitEnd(){
                if(found[0]!=1)throw new IllegalStateException("Expected one private creative apply, got "+found[0]);
                MethodVisitor m=super.visitMethod(ACC_PRIVATE|ACC_STATIC,"apply",DESC,null,null);
                m.visitCode();
                m.visitVarInsn(ALOAD,0);
                m.visitMethodInsn(INVOKESTATIC,"cn/ssjjdark/DarkLiberationBypass","open","("+ENTITY+")L"+SCOPE+";",false);
                m.visitVarInsn(ASTORE,5);
                Label begin=new Label(),end=new Label(),failure=new Label();
                m.visitTryCatchBlock(begin,end,failure,"java/lang/Throwable");
                m.visitLabel(begin);
                m.visitVarInsn(ALOAD,0);m.visitVarInsn(ALOAD,1);
                m.visitVarInsn(ALOAD,1);m.visitVarInsn(FLOAD,2);
                m.visitMethodInsn(INVOKESTATIC,"cn/ssjjdark/DarkUltimateDamage","scale","("+SOURCE+"F)F",false);
                m.visitVarInsn(DLOAD,3);
                m.visitMethodInsn(INVOKESTATIC,OWNER,ORIGINAL,DESC,false);
                m.visitVarInsn(ISTORE,6);
                m.visitLabel(end);
                m.visitVarInsn(ALOAD,5);m.visitMethodInsn(INVOKEVIRTUAL,SCOPE,"close","()V",false);
                m.visitVarInsn(ILOAD,6);m.visitInsn(IRETURN);
                m.visitLabel(failure);
                m.visitFrame(F_FULL,5,new Object[]{"net/minecraft/world/entity/LivingEntity","net/minecraft/world/damagesource/DamageSource",FLOAT,DOUBLE,SCOPE},1,new Object[]{"java/lang/Throwable"});
                m.visitVarInsn(ASTORE,7);
                m.visitVarInsn(ALOAD,5);m.visitMethodInsn(INVOKEVIRTUAL,SCOPE,"close","()V",false);
                m.visitVarInsn(ALOAD,7);m.visitInsn(ATHROW);
                m.visitMaxs(5,8);m.visitEnd();super.visitEnd();
            }
        };
        reader.accept(visitor,0);
        return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("BASE runtime report");
        Path runtime=Path.of(args[1]);Map<String,byte[]> entries=new LinkedHashMap<>();
        try(var jar=new JarFile(runtime.toFile())){
            for(var entry:Collections.list(jar.entries()))if(!entry.isDirectory())entries.put(entry.getName(),jar.getInputStream(entry).readAllBytes());
        }
        if(entries.containsKey(ENTRY))throw new IllegalStateException("Unexpected CreativeDamage source override or repeated patch");
        byte[] baseline;
        try(var jar=new JarFile(args[0])){baseline=jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(ENTRY))).readAllBytes();}
        byte[] patched=patch(baseline);
        Map<String,Object> report=new LinkedHashMap<>(AuditUltimateDamage.audit(baseline,patched));
        entries.put(ENTRY,patched);
        Path output=runtime.resolveSibling(runtime.getFileName()+".ultimate-damage");
        try(var jar=new JarOutputStream(Files.newOutputStream(output))){
            for(var entry:entries.entrySet()){jar.putNextEntry(new JarEntry(entry.getKey()));jar.write(entry.getValue());jar.closeEntry();}
        }
        Files.move(output,runtime,StandardCopyOption.REPLACE_EXISTING);
        report.put("passed",true);report.put("live_game_test",false);
        report.put("scope","One creative-only wrapper: scoped defense bypass plus one fixed +99% outgoing bonus during an ultimate session; all original settlement guards retained.");
        FreshRemap.report(Path.of(args[2]),report);
        System.out.println("PASS creative-only scoped settlement wrapper and exact baseline-body preservation");
    }
}
