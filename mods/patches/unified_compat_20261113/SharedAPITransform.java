import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import com.google.gson.*;

/** Normalize production API call sites first; the selected backend is supplied as compiled classes. */
public final class SharedAPITransform {
    private static final String API = "cn/fusionarsenal/compat/TaCZClientCompat";
    private static final String DISPLAY = "com/tacz/guns/client/resource/GunDisplayInstance";
    private static final String KEEP = "com/tacz/guns/api/client/other/KeepingItemRenderer";
    private static final String ACTIVE = "com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler";
    private static final String INSTANCE = "com/github/mcmodderanchor/simplebedrockmodel/v1/client/animation/IFPAnimationInstance";
    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void staticCall(MethodInsnNode call, String name, String desc) {
        call.setOpcode(Opcodes.INVOKESTATIC); call.owner = API; call.name = name; call.desc = desc; call.itf = false;
    }
    public static void main(String[] args) throws Exception {
        // actual input mod.jar, compiled API root, output overlay.jar, report.json
        Path source = Path.of(args[0]), backend = Path.of(args[1]), output = Path.of(args[2]), reportPath = Path.of(args[3]);
        var changed = new TreeMap<String, Map<String, Integer>>();
        var counts = new TreeMap<String, Integer>();
        var rows = new ArrayList<Map<String, Object>>();
        var removed = List.of("cn/ssjjcompat/TaCZSounds.class");
        try (var input = new JarFile(source.toFile()); var out = new JarOutputStream(Files.newOutputStream(output))) {
            for (var entry : Collections.list(input.entries())) {
                if (!entry.getName().endsWith(".class") || removed.contains(entry.getName())) continue;
                byte[] bytes = input.getInputStream(entry).readAllBytes();
                ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
                var edits = new TreeMap<String, Integer>(); boolean remapInstance = false;
                if (node.name.equals("cn/ssjjcompat/WeaponAssetWarmup")) {
                    int entryHooks = 0;
                    for (var method : node.methods) if (method.name.equals("prepare") && method.desc.equals("()V")) {
                        method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, API, "onResourceReload", "()V", false)); entryHooks++;
                    }
                    check(entryHooks == 1, "Unexpected resource-reload coordinator");
                    edits.put("reloadMetadata", 1); counts.merge("reloadMetadata", 1, Integer::sum);
                }
                for (var method : node.methods) for (var instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode call)) continue;
                    String old = call.owner + "." + call.name + call.desc; String kind = null;
                    if (call.owner.equals(DISPLAY) && call.desc.equals("()V") && (call.name.equals("warmUpModel") || call.name.equals("warmUpRuntime"))) {
                        kind = "warmup";
                        staticCall(call, call.name.equals("warmUpModel") ? "warmModel" : "warmRuntime", "(L" + DISPLAY + ";)V");
                    } else if (call.owner.equals("cn/ssjjcompat/TaCZSounds")) {
                        kind = "sound"; call.owner = API;
                    } else if (call.owner.equals(KEEP) && call.name.equals("getRenderer")) {
                        kind = "retainedRenderer"; staticCall(call, "retainedRenderer", call.desc);
                    } else if (call.owner.equals(KEEP) && call.name.equals("getCurrentItem")) {
                        kind = "retainedItem"; staticCall(call, "retainedItem", "(L" + KEEP + ";)Lnet/minecraft/world/item/ItemStack;");
                    } else if (call.owner.equals(ACTIVE) && call.name.equals("getActiveAnimationInstance")) {
                        kind = "activeItem";
                        staticCall(call, "currentRenderedItem", "()Lnet/minecraft/world/item/ItemStack;"); remapInstance = true;
                    } else if (call.owner.equals(INSTANCE) && call.name.equals("currentItem")) {
                        kind = "activeItemExtraction"; method.instructions.remove(call); remapInstance = true;
                    } else if (node.name.equals("cn/ssjjcompat/WeaponAssetWarmup") && call.owner.equals(DISPLAY) && call.name.equals("getGunModel")) {
                        kind = "reloadPrepare";
                        staticCall(call, "prepareDisplay", "(L" + DISPLAY + ";)Lcom/tacz/guns/client/model/BedrockGunModel;");
                    } else if (node.name.equals("cn/ssjjcompat/WeaponAssetWarmup") && call.owner.equals(DISPLAY) && call.name.equals("getAnimationStateMachine")) {
                        var receiver = previousCode(call); var pop = nextCode(call);
                        check(receiver instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && pop != null && pop.getOpcode() == Opcodes.POP, "Unexpected reload animation preparation layout");
                        kind = "reloadPrepareDeduplicated";
                        method.instructions.remove(receiver); method.instructions.remove(call); method.instructions.remove(pop);
                    }
                    if (kind != null) {
                        edits.merge(kind, 1, Integer::sum); counts.merge(kind, 1, Integer::sum);
                        rows.add(Map.of("class", node.name, "method", method.name + method.desc, "kind", kind, "original_call", old));
                    }
                }
                if (edits.isEmpty()) continue;
                if (remapInstance) {
                    ClassNode mapped = new ClassNode();
                    node.accept(new ClassRemapper(mapped, new SimpleRemapper(INSTANCE, "net/minecraft/world/item/ItemStack"))); node = mapped;
                }
                for (var method : node.methods) if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0)
                    new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
                ClassWriter writer = new ClassWriter(0); node.accept(writer); bytes = writer.toByteArray();
                out.putNextEntry(new JarEntry(entry.getName())); out.write(bytes); out.closeEntry();
                changed.put(entry.getName(), edits);
            }
            try (var files = Files.walk(backend)) {
                for (Path path : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                    String name = backend.relativize(path).toString().replace('\\', '/');
                    check(name.startsWith("cn/fusionarsenal/compat/"), "Unexpected backend output: " + name);
                    out.putNextEntry(new JarEntry(name)); out.write(Files.readAllBytes(path)); out.closeEntry();
                }
            }
        }
        check(counts.getOrDefault("warmup", 0) == 3, "Warmup call inventory changed");
        check(counts.getOrDefault("sound", 0) == 2, "Sound bridge inventory changed");
        check(counts.getOrDefault("retainedRenderer", 0) == 4 && counts.getOrDefault("retainedItem", 0) == 4, "Keeping renderer inventory changed");
        check(counts.getOrDefault("activeItem", 0) == 1 && counts.getOrDefault("activeItemExtraction", 0) == 1, "Drawn item inventory changed");
        check(counts.getOrDefault("reloadPrepare", 0) == 1 && counts.getOrDefault("reloadPrepareDeduplicated", 0) == 1, "Reload preparation inventory changed");
        check(counts.getOrDefault("reloadMetadata", 0) == 1, "Mount metadata reload was not registered once");
        var report = Map.of("source", source.toString(), "overlay", output.toString(), "changed_classes", changed,
            "removed_classes", removed, "counts", counts, "call_sites", rows, "basic_verified", true,
            "scope", "Only client integration call sites; resources, registry IDs, channels, gameplay conditions and numbers untouched");
        Files.writeString(reportPath, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("Shared API overlay: " + changed.size() + " changed classes, " + counts + "; old sound bridge removed");
    }
}
