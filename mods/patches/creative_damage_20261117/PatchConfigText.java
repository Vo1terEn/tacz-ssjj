import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;

/** Change only the two creative-stone descriptions in the current runtime config. */
public final class PatchConfigText {
    private static final String CLASS="cn/fusionarsenal/config/ArsenalConfig.class";
    private static final Map<String,String> TEXT=new LinkedHashMap<>();
    static {
        TEXT.put("【4. 创造强化石：常驻属性与预留开关】","【4. 创造强化石：常驻属性与高级直伤】");
        TEXT.put("创造强化石增强攻击预留开关：true开启，false关闭。具体增强攻击功能尚未实现，本选项当前不改变攻击行为。",
            "创造强化石高级直伤开关：true开启，false恢复普通伤害。绕过受击取消、限伤减伤和受击冷却；探测可识别私有血池，并保留原生死亡、掉落和击杀结算。复杂血池需专门适配。");
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String canonical(byte[] bytes,boolean normalizeOldText) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if (normalizeOldText) for (MethodNode method:node.methods)
            for (AbstractInsnNode insn:method.instructions)
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String value && TEXT.containsKey(value))
                    ldc.cst=TEXT.get(value);
        StringWriter result=new StringWriter();
        node.accept(new TraceClassVisitor(new PrintWriter(result)));
        return result.toString();
    }

    public static void main(String[] args) throws Exception {
        byte[] original;
        try (ZipFile baseline=new ZipFile(args[0])) {
            original=baseline.getInputStream(baseline.getEntry(CLASS)).readAllBytes();
        }
        Map<String,Integer> hits=new LinkedHashMap<>();for (String text:TEXT.keySet()) hits.put(text,0);
        ClassReader reader=new ClassReader(original);
        ClassWriter writer=new ClassWriter(reader,0);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                MethodVisitor out=super.visitMethod(access,name,desc,signature,exceptions);
                if (!name.equals("<clinit>")) return out;
                return new MethodVisitor(Opcodes.ASM9,out) {
                    @Override public void visitLdcInsn(Object value) {
                        if (value instanceof String text && TEXT.containsKey(text)) {
                            hits.put(text,hits.get(text)+1);value=TEXT.get(text);
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        },0);
        for (var hit:hits.entrySet())
            if (hit.getValue()!=1) throw new AssertionError("Expected exactly one old description LDC: "+hit);
        byte[] modified=writer.toByteArray();
        if (!canonical(original,true).equals(canonical(modified,false)))
            throw new AssertionError("Configuration bytecode changed beyond the two descriptions");
        Path output=Path.of(args[1]);Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,modified);
        FreshRemap.report(Path.of(args[2]),Map.of("passed",true,"class",CLASS,
            "baseline_class_sha256",hash(original),"patched_class_sha256",hash(modified),
            "replacement_ldc_counts",hits,"two_description_changes_only",true,
            "existing_configuration_definitions_preserved",true));
        System.out.println("PASS current ArsenalConfig patched: two description LDCs; all other bytecode preserved");
    }
}
