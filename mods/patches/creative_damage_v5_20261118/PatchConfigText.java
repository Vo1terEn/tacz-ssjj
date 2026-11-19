import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;

/** Replace one description constant, preserving the current complete config ABI. */
public final class PatchConfigText {
    private static final String CLASS="cn/fusionarsenal/config/ArsenalConfig.class";
    private static String canonical(byte[] bytes,String before,String after) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof LdcInsnNode ldc&&before.equals(ldc.cst))ldc.cst=after;
        StringWriter text=new StringWriter();node.accept(new TraceClassVisitor(new PrintWriter(text)));
        return text.toString();
    }
    public static void main(String[] args)throws Exception {
        byte[] original;try(ZipFile zip=new ZipFile(args[0])){original=zip.getInputStream(zip.getEntry(CLASS)).readAllBytes();}
        String before=Files.readString(Path.of(args[1])),after=Files.readString(Path.of(args[2]));
        ClassNode node=new ClassNode();ClassReader reader=new ClassReader(original);reader.accept(node,0);int hits=0;
        for(MethodNode method:node.methods)if(method.name.equals("<clinit>"))for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof LdcInsnNode ldc&&before.equals(ldc.cst)){ldc.cst=after;hits++;}
        if(hits!=1)throw new AssertionError("Expected one existing switch description: "+hits);
        ClassWriter writer=new ClassWriter(reader,0);node.accept(writer);byte[] updated=writer.toByteArray();
        if(!canonical(original,before,after).equals(canonical(updated,after,after)))throw new AssertionError("Config changed beyond its description");
        Path output=Path.of(args[3]);Files.createDirectories(output.getParent());Files.write(output,updated);
        FreshRemap.report(Path.of(args[4]),Map.of("passed",true,"class",CLASS,"description_constants_replaced",hits,
            "existing_configuration_definitions_preserved",true,"all_other_bytecode_preserved",true));
        System.out.println("PASS existing config switch: one description updated, all definitions/defaults preserved");
    }
}
