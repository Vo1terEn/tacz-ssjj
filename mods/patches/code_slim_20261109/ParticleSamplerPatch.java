import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.io.*;

/** Patches only the shipping state methods after matching their original source instructions. */
public final class ParticleSamplerPatch {
    static final String PREFIX = "cn/ssjjdimension/client/";
    static final List<String> NAMES = List.of("DimensionParticles", "DimensionFieldGeometry", "DimensionMarkGeometry");
    static final String HELPER = "cn/ssjjcompat/client/ParticleFrames";

    static ClassNode node(byte[] bytes) {
        var n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n;
    }
    static MethodNode state(ClassNode n) {
        return n.methods.stream().filter(m -> m.name.equals("state") && m.desc.equals("(L"+n.name+"$Particle;F)[F"))
            .reduce((a,b) -> { throw new AssertionError("Duplicate state method"); }).orElseThrow();
    }
    static String instructions(MethodNode method, String owner) {
        var clean = new MethodNode(method.access, method.name, method.desc.replace(owner,"SAMPLER"), method.signature, null);
        method.accept(clean);
        // Exclude source lines, local names, frames and literal particle owner only.
        var labels = new HashMap<LabelNode,Integer>(); int count = 0;
        for (var x : clean.instructions) if (x instanceof LabelNode label) labels.put(label,count++);
        var out = new StringBuilder();
        for (var x : clean.instructions) {
            if (x.getOpcode() < 0) continue;
            out.append(x.getOpcode()).append(' ');
            if (x instanceof VarInsnNode v) out.append(v.var);
            else if (x instanceof IntInsnNode v) out.append(v.operand);
            else if (x instanceof TypeInsnNode v) out.append(v.desc);
            else if (x instanceof FieldInsnNode v) out.append(v.owner.replace(owner,"SAMPLER")).append('.').append(v.name).append(v.desc);
            else if (x instanceof MethodInsnNode v) out.append(v.owner).append('.').append(v.name).append(v.desc).append(':').append(v.itf);
            else if (x instanceof JumpInsnNode v) out.append(labels.get(v.label));
            else if (x instanceof IincInsnNode v) out.append(v.var).append(':').append(v.incr);
            else if (x instanceof LdcInsnNode v) out.append(v.cst);
            else if (!(x instanceof InsnNode)) throw new AssertionError("Unexpected sampler instruction: "+x);
            out.append('\n');
        }
        return out.toString();
    }
    static byte[] outsideState(byte[] bytes) {
        var n = node(bytes); n.methods.remove(state(n)); var w = new ClassWriter(0); n.accept(w); return w.toByteArray();
    }
    static void writeJson(Path path, Object value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(value)+"\n");
    }
    public static void main(String[] args) throws Exception {
        Path baseline=Path.of(args[0]), reference=Path.of(args[1]), overlay=Path.of(args[2]), evidence=Path.of(args[3]);
        var reports=new ArrayList<Map<String,Object>>(); var originalInstructions=new HashMap<String,String>();
        try(var jar=new JarFile(baseline.toFile())) {
            for(var name:NAMES) {
                String entry=PREFIX+name+".class";
                byte[] bytes;try(var in=jar.getInputStream(jar.getJarEntry(entry))){bytes=in.readAllBytes();}
                var original=node(bytes);var method=state(original);
                String actual=instructions(method,original.name);
                var ref=node(Files.readAllBytes(reference.resolve(entry)));
                if(!actual.equals(instructions(state(ref),ref.name)))throw new AssertionError("Original source state instruction mismatch: "+entry);
                originalInstructions.put(name,actual);
                Files.createDirectories(evidence);
                Files.writeString(evidence.resolve(name+"_original_instructions.txt"),actual);
                int before=method.instructions.size();
                method.instructions.clear();method.tryCatchBlocks.clear();
                if(method.localVariables!=null)method.localVariables.clear();
                method.visibleLocalVariableAnnotations=null;method.invisibleLocalVariableAnnotations=null;
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
                method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,original.name+"$Particle","frames","[[F"));
                method.instructions.add(new VarInsnNode(Opcodes.FLOAD,1));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"sample","([[FF)[F",false));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                method.maxStack=2;method.maxLocals=2;
                var writer=new ClassWriter(0);original.accept(writer);byte[] output=writer.toByteArray();
                if(!Arrays.equals(outsideState(bytes),outsideState(output)))throw new AssertionError("Outside-state class changed: "+entry);
                var checked=node(output);
                for(var m:checked.methods) new Analyzer<>(new BasicVerifier()).analyze(checked.name,m);
                Path target=overlay.resolve(entry);Files.createDirectories(target.getParent());Files.write(target,output);
                reports.add(Map.of("entry",entry,"source_state_instructions_match",true,"all_other_methods_fields_metadata_preserved",true,
                    "old_instruction_nodes",before,"new_instruction_nodes",5,"old_class_bytes",bytes.length,"new_class_bytes",output.length));
            }
        }
        if(!originalInstructions.get("DimensionFieldGeometry").equals(originalInstructions.get("DimensionMarkGeometry")))
            throw new AssertionError("Field/mark original instruction trees diverge");
        writeJson(evidence.resolve("patch.json"),Map.of("passed",true,"patched_methods",reports,"field_mark_original_instruction_trees_identical",true,
            "particles_difference","Caches selected rows in locals; same clamp, multiply, add/subtract order and normalize/slerp/normalize calls.",
            "preserved_contract","Original state signatures and DTO classes; no extra input guards, validation or floating point algebra changes."));
        System.out.println("PASS three shipping state methods delegate to ParticleFrames.sample; all outside-state bytecode preserved");
    }
}
