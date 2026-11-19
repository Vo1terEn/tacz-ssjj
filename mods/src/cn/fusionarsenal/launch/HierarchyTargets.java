package cn.fusionarsenal.launch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;

/** Uses Forge's completed class metadata, without defining any inspected class. */
public final class HierarchyTargets {
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private HierarchyTargets() {}

    public static Set<String> discover() throws ReflectiveOperationException {
        ClassLoader serviceLoader = HierarchyTargets.class.getClassLoader();
        Class<?> fml = Class.forName("net.minecraftforge.fml.loading.FMLLoader", false, serviceLoader);
        Object loading = fml.getMethod("getLoadingModList").invoke(null);
        if (loading == null) throw new IllegalStateException("Forge loading metadata is not available yet");
        var parents = new LinkedHashMap<String,String>();
        java.lang.reflect.Method typeAccessor = null, parentAccessor = null;
        Iterable<?> files = (Iterable<?>)loading.getClass().getMethod("getModFiles").invoke(loading);
        for (Object info : files) {
            Object file = info.getClass().getMethod("getFile").invoke(info);
            // Forge waits for the already-submitted background scan here. This is
            // called only after completeScan, before the GAME layer is constructed.
            Object scan = file.getClass().getMethod("getScanResult").invoke(file);
            if (scan == null) continue;
            Iterable<?> classes = (Iterable<?>)scan.getClass().getMethod("getClasses").invoke(scan);
            for (Object data : classes) {
                if (typeAccessor == null) {
                    typeAccessor = data.getClass().getMethod("clazz");
                    parentAccessor = data.getClass().getMethod("parent");
                }
                org.objectweb.asm.Type type = (org.objectweb.asm.Type)typeAccessor.invoke(data);
                org.objectweb.asm.Type parent = (org.objectweb.asm.Type)parentAccessor.invoke(data);
                String name = type.getInternalName();
                String base = parent == null ? null : parent.getInternalName();
                parents.put(name, base);
            }
        }
        return select(parents, name -> {
            try {
                Path resource = (Path)loading.getClass().getMethod("findResource", String.class).invoke(loading, name + ".class");
                if (resource != null && Files.isRegularFile(resource)) {
                    try (var input = Files.newInputStream(resource)) { return new ClassReader(input).getSuperName(); }
                }
                try (var input = serviceLoader.getResourceAsStream(name + ".class")) {
                    return input == null ? null : new ClassReader(input).getSuperName();
                }
            } catch (Exception unavailable) { return null; }
        });
    }

    /** Public metadata-only entry point for regression fixtures. */
    public static Set<String> select(Map<String,String> supplied, Function<String,String> missingParent) {
        var parents = new HashMap<>(supplied);
        var known = new HashMap<String,Boolean>();
        known.put(LIVING, true);
        known.put("java/lang/Object", false);
        var result = new LinkedHashSet<String>();
        for (String type : supplied.keySet()) {
            var path = new LinkedHashSet<String>();
            String current = type;
            while (current != null && !known.containsKey(current) && path.add(current)) {
                if (!parents.containsKey(current)) parents.put(current, missingParent.apply(current));
                current = parents.get(current);
            }
            boolean living = current != null && Boolean.TRUE.equals(known.get(current));
            for (String owner : path) known.put(owner, living);
            if (living) result.add(type.replace('/', '.'));
        }
        // These are public Forge/Minecraft entry points, not third-party targets.
        result.add("net.minecraft.world.entity.LivingEntity");
        result.add("net.minecraft.world.entity.Entity");
        result.add("net.minecraft.world.entity.player.Player");
        result.add("net.minecraft.server.level.ServerPlayer");
        result.add("net.minecraftforge.common.ForgeHooks");
        return Collections.unmodifiableSet(result);
    }
}
