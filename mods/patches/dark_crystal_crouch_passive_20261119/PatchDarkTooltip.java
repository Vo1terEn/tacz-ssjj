import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Disable only the obsolete appended Dark Crystal description event body. */
public final class PatchDarkTooltip {
    private static final String NAME="tooltipDarkCrystal";
    private static final String DESC="(Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;)V";
    private static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    public static void main(String[] args)throws Exception {
        if(args.length<2||args.length>3)throw new IllegalArgumentException("input.class output.class [report.json]");
        byte[] before=Files.readAllBytes(Path.of(args[0]));ClassReader reader=new ClassReader(before);ClassNode node=new ClassNode();reader.accept(node,0);
        if(!node.name.equals("cn/ssjjdark/DarkInput"))throw new AssertionError("Wrong class: "+node.name);
        var targets=node.methods.stream().filter(m->m.name.equals(NAME)&&m.desc.equals(DESC)).toList();
        if(targets.size()!=1)throw new AssertionError("Expected exactly one tooltip event method");
        MethodNode target=targets.get(0);boolean clear=false,melee=false,damage=false,ultimate=false;
        for(var instruction:target.instructions){
            if(instruction instanceof MethodInsnNode m&&m.owner.equals("java/util/List")&&m.name.equals("clear"))clear=true;
            if(instruction instanceof LdcInsnNode l&&l.cst instanceof String s){melee|=s.equals("近战武器");damage|=s.equals("左键伤害：12");ultimate|=s.startsWith("终极强化石／创造强化石：");}
        }
        if(!clear||!melee||!damage||!ultimate)throw new AssertionError("Expected the frozen19 destructive tooltip body; refuse unrelated patch");
        // The reader-backed writer copies every unmodified method_info directly.
        // Retain the registered signature/annotations, replacing only this Code.
        ClassWriter writer=new ClassWriter(reader,0);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                MethodVisitor destination=super.visitMethod(access,name,desc,signature,exceptions);
                if(!name.equals(NAME)||!desc.equals(DESC))return destination;
                return new MethodVisitor(Opcodes.ASM9){
                    @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible){return destination.visitAnnotation(desc,visible);}
                    @Override public AnnotationVisitor visitTypeAnnotation(int ref,TypePath path,String desc,boolean visible){return destination.visitTypeAnnotation(ref,path,desc,visible);}
                    @Override public void visitParameter(String name,int access){destination.visitParameter(name,access);}
                    @Override public void visitEnd(){destination.visitCode();destination.visitInsn(Opcodes.RETURN);destination.visitMaxs(0,1);destination.visitEnd();}
                };
            }
        },0);
        byte[] after=writer.toByteArray();ClassNode verify=new ClassNode();new ClassReader(after).accept(verify,0);
        var patched=verify.methods.stream().filter(m->m.name.equals(NAME)&&m.desc.equals(DESC)).findFirst().orElseThrow();
        long count=0;for(var instruction:patched.instructions)if(instruction.getOpcode()>=0){if(instruction.getOpcode()!=Opcodes.RETURN)throw new AssertionError("Unexpected emitted instruction");count++;}
        if(count!=1||verify.methods.size()!=node.methods.size()||verify.fields.size()!=node.fields.size())throw new AssertionError("Patch scope changed");
        Path output=Path.of(args[1]);Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,after);
        if(args.length==3){Path report=Path.of(args[2]);Files.createDirectories(report.toAbsolutePath().getParent());Files.writeString(report,"{\n  \"passed\": true,\n  \"class\": \"cn/ssjjdark/DarkInput\",\n  \"method\": \"tooltipDarkCrystal(ItemTooltipEvent)\",\n  \"only_replacement_instruction\": \"RETURN\",\n  \"reader_backed_writer_preserves_other_methods\": true,\n  \"before_class_sha256\": \""+sha(before)+"\",\n  \"after_class_sha256\": \""+sha(after)+"\"\n}\n");}
        System.out.println("PASS only obsolete DarkInput.tooltipDarkCrystal event body replaced by RETURN");
    }
}
