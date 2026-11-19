package cn.fusionarsenal.launch;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Loading-stage observation only; later launch plugins can still change a body. */
public final class HealthBytecode {
    private HealthBytecode() {}
    private static final Map<ClassLoader,Map<String,byte[]>> BYTES = new WeakHashMap<>();
    private static final Map<ClassLoader,Map<String,Long>> VERSIONS = new WeakHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final AtomicLong TARGETS = new AtomicLong();
    private static final AtomicLong CLASSES = new AtomicLong();
    private static final AtomicLong METHODS = new AtomicLong();
    private static final AtomicLong CAPTURE_FAILURES = new AtomicLong();
    private static final AtomicLong FALLBACKS = new AtomicLong();
    private static final AtomicLong AMBIGUITIES = new AtomicLong();
    private static volatile boolean hierarchyDiscovery;

    public static synchronized void remember(ClassLoader loader, String internalName, byte[] bytes) {
        var names = BYTES.computeIfAbsent(loader, ignored -> new HashMap<>());
        if (!Arrays.equals(names.get(internalName), bytes)) {
            names.put(internalName, bytes.clone());
            VERSIONS.computeIfAbsent(loader, ignored -> new HashMap<>()).put(internalName, SEQUENCE.incrementAndGet());
        }
    }
    public static synchronized byte[] classBytes(Class<?> type) {
        Observation found = observation(type);
        return found == null ? null : found.bytes().clone();
    }
    public static synchronized long classRevision(Class<?> type) {
        Observation found = observation(type);
        return found == null ? 0 : found.revision();
    }
    private record Observation(byte[] bytes, long revision) { }
    private static Observation observation(Class<?> type) {
        if (type == null) return null;
        String name = type.getName().replace('.', '/');
        var direct = BYTES.get(type.getClassLoader());
        if (direct != null && direct.containsKey(name))
            return new Observation(direct.get(name), VERSIONS.get(type.getClassLoader()).get(name));
        byte[] unique = null; long revision = 0;
        // A launch callback exposes TCCL rather than the eventual defining loader.
        // A cross-loader observation is usable only if every body of this name
        // still in the weak cache is byte-for-byte identical. It is not final-body
        // proof, and ambiguity always falls back to the caller's resource path.
        for (var entry : BYTES.entrySet()) {
            byte[] candidate = entry.getValue().get(name);
            if (candidate == null) continue;
            if (unique != null && !Arrays.equals(unique, candidate)) { AMBIGUITIES.incrementAndGet(); return null; }
            unique = candidate;
            var versions = VERSIONS.get(entry.getKey());
            if (versions != null) revision = Math.max(revision, versions.getOrDefault(name, 0L));
        }
        if (unique == null) return null;
        FALLBACKS.incrementAndGet();
        return new Observation(unique, revision);
    }
    public static void targetsRegistered(int count) { TARGETS.set(count); }
    public static void hierarchyDiscovered(boolean available) { hierarchyDiscovery = available; }
    public static void transformed(int methods) {
        if (methods > 0) { CLASSES.incrementAndGet(); METHODS.addAndGet(methods); }
    }
    public static void captureFailed(Throwable failure) {
        if (CAPTURE_FAILURES.incrementAndGet() <= 3)
            System.err.println("[SSJJRPG] Health load-time byte observation failed: " + failure);
    }
    public static synchronized Map<String,Long> diagnostics() {
        var values = new LinkedHashMap<String,Long>();
        values.put("hierarchyDiscovery", hierarchyDiscovery ? 1L : 0L);
        values.put("targetClasses", TARGETS.get());
        values.put("modifiedClasses", CLASSES.get()); values.put("modifiedMethods", METHODS.get());
        values.put("cachedLoaders", (long)BYTES.size());
        values.put("cachedBodies", BYTES.values().stream().mapToLong(Map::size).sum());
        values.put("captureFailures", CAPTURE_FAILURES.get());
        values.put("uniqueNameFallbacks", FALLBACKS.get()); values.put("ambiguousCacheQueries", AMBIGUITIES.get());
        return Collections.unmodifiableMap(values);
    }
}
