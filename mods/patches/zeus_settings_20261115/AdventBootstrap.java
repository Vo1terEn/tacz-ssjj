import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/** Two bounded hooks leave every original instruction and unrelated method intact. */
public final class AdventBootstrap {
    static void patch(JarFile jar,Path output,String path,boolean bootstrap)throws Exception {
        ClassReader reader=new ClassReader(jar.getInputStream(jar.getJarEntry(path)));
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] matches={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                MethodVisitor original=super.visitMethod(access,name,desc,signature,exceptions);
                if(bootstrap&&name.equals("<init>")) {
                    matches[0]++;return new MethodVisitor(Opcodes.ASM9,original){
                        boolean inserted;
                        @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                            super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                            if(!inserted&&opcode==Opcodes.INVOKESPECIAL&&method.equals("<init>")){
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,"cn/ssjjzeus/ZeusAdventNetwork","init","()V",false);inserted=true;
                            }
                        }
                        @Override public void visitEnd(){if(!inserted)throw new IllegalStateException("Missing super call");super.visitEnd();}
                    };
                }
                if(!bootstrap&&name.equals("reserved")&&desc.equals("(Lnet/minecraftforge/client/event/InputEvent$Key;)Z")) {
                    matches[0]++;return new MethodVisitor(Opcodes.ASM9,original){
                        @Override public void visitCode(){
                            super.visitCode();Label originalBody=new Label();
                            super.visitVarInsn(Opcodes.ALOAD,0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"cn/ssjjzeus/client/ZeusAdventKeys","reserved",desc,false);
                            super.visitJumpInsn(Opcodes.IFEQ,originalBody);super.visitInsn(Opcodes.ICONST_1);super.visitInsn(Opcodes.IRETURN);
                            super.visitLabel(originalBody);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                        }
                    };
                }
                return original;
            }
        },0);
        if(matches[0]!=1)throw new IllegalStateException("Unexpected hook count "+matches[0]);
        Path p=output.resolve(path);Files.createDirectories(p.getParent());Files.write(p,writer.toByteArray());
    }
    public static void main(String[] args)throws Exception{
        try(JarFile jar=new JarFile(args[0])){
            patch(jar,Path.of(args[1]),"cn/punishmenteye/EyeCrosshair.class",true);
            patch(jar,Path.of(args[1]),"cn/judgfire/JudgmentSkillKey.class",false);
        }
        System.out.println("PASS early Advent channel and Zeus-only V guard; original instruction bodies preserved");
    }
}
