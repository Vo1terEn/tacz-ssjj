import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Wires the new config into the original registry and preserves the compiled skill bodies. */
public final class PatchAresAccessoryConfig implements Opcodes {
    private static final String CONFIG = "cn/ssjjares/AresAccessoryConfig";
    private static final String EFFECTS = "cn/ssjjares/AresCombat$Accessories";

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]);
        try (var original = new ZipFile(args[0])) {
            patch(original, output, "cn/fusionarsenal/config/ArsenalConfig", PatchAresAccessoryConfig::register);
            patch(original, output, "cn/ssjjares/AresRage", PatchAresAccessoryConfig::rage);
            patch(original, output, "cn/ssjjares/AresCannons", PatchAresAccessoryConfig::cannons);
        }
    }

    private static void patch(ZipFile original, Path output, String name, Consumer<ClassNode> action) throws Exception {
        var entry = original.getEntry(name + ".class");
        var type = new ClassNode();
        try (var stream = original.getInputStream(entry)) {
            new ClassReader(stream).accept(type, 0);
        }
        action.accept(type);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        var file = output.resolve(entry.getName());
        Files.createDirectories(file.getParent());
        Files.write(file, writer.toByteArray());
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        return owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow();
    }

    private static <T extends AbstractInsnNode> T anchor(MethodNode method, Class<T> type, Predicate<T> matches) {
        var found = new ArrayList<T>();
        for (var next : method.instructions) {
            if (type.isInstance(next) && matches.test(type.cast(next))) found.add(type.cast(next));
        }
        if (found.size() != 1) throw new IllegalStateException(method.name + method.desc + ": " + found.size() + " anchors");
        return found.get(0);
    }

    private static InsnList instructions(AbstractInsnNode... nodes) {
        var result = new InsnList();
        for (var node : nodes) result.add(node);
        return result;
    }

    private static void register(ClassNode type) {
        var clinit = method(type, "<clinit>", "()V");
        var define = anchor(clinit, MethodInsnNode.class,
            call -> call.owner.equals("cn/ssjjdark/DarkCrystalConfig") && call.name.equals("define"));
        clinit.instructions.insert(define, instructions(new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, 2),
            new MethodInsnNode(INVOKESTATIC, CONFIG, "define", define.desc, false)));
    }

    private static void rage(ClassNode type) {
        var activate = method(type, "activate", "(Lnet/minecraft/server/level/ServerPlayer;)V");
        var mask = anchor(activate, FieldInsnNode.class, field -> field.owner.equals(EFFECTS) && field.name.equals("mask"));
        var original = anchor(activate, MethodInsnNode.class,
            call -> call.owner.equals(type.name) && call.name.equals("cooldownTicks") && call.desc.equals("(Z)I"));
        mask.name = "maskTier";
        mask.desc = "I";
        original.owner = CONFIG;
        original.name = "rageCooldownTicks";
        original.desc = "(I)I";
    }

    private static void cannons(ClassNode type) {
        var tick = method(type, "tick", "(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V");
        var tier = anchor(tick, FieldInsnNode.class, field -> field.owner.equals(EFFECTS) && field.name.equals("shoulderTier"));
        AbstractInsnNode next = tier.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        if (!(next instanceof VarInsnNode store) || store.getOpcode() != ISTORE)
            throw new IllegalStateException("Missing equipped shoulder tier");
        var interval = anchor(tick, LdcInsnNode.class, load -> Long.valueOf(200).equals(load.cst));
        tick.instructions.insertBefore(interval, instructions(new VarInsnNode(ILOAD, store.var),
            new MethodInsnNode(INVOKESTATIC, CONFIG, "missileIntervalTicks", "(I)I", false), new InsnNode(I2L)));
        tick.instructions.remove(interval);
    }
}
