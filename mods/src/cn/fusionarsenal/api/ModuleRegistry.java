package cn.fusionarsenal.api;

import java.util.*;

/** Ordered explicit registration; requires only the JDK and never discovers or loads game classes. */
public final class ModuleRegistry {
    public enum State { OPEN, INITIALIZING, READY, FAILED }

    /** Detached immutable metadata; the executable module object is intentionally not exposed. */
    public record ModuleInfo(String id, Set<String> gunIds) {
        public ModuleInfo {
            requireId(id);
            var captured = new LinkedHashSet<>(Objects.requireNonNull(gunIds,"gunIds"));
            if(captured.isEmpty())throw new IllegalArgumentException("A weapon module must own a gun");
            captured.forEach(ModuleRegistry::requireId);
            gunIds = Set.copyOf(captured);
        }
    }
    private record Registered(ModuleInfo info, WeaponModule implementation) {}
    private final Map<String,Registered> modules = new LinkedHashMap<>();
    private final Map<String,ModuleInfo> owners = new HashMap<>();
    private State state = State.OPEN;
    private Throwable failure;
    private String failedModule;

    private static void requireId(String id) {
        if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Expected stable namespace:path ID: "+id);
    }
    public synchronized ModuleRegistry register(WeaponModule module) {
        if(state!=State.OPEN)throw new IllegalStateException("Module registration is closed: "+state);
        Objects.requireNonNull(module,"module");
        // Capture metadata once. Mutating a provider's original set cannot change ownership.
        var info=new ModuleInfo(module.id(),module.gunIds());
        if(modules.containsKey(info.id()))throw new IllegalArgumentException("Duplicate weapon module: "+info.id());
        for(String gun:info.gunIds())if(owners.containsKey(gun))
            throw new IllegalArgumentException("Gun "+gun+" already belongs to "+owners.get(gun).id());
        // Complete validation precedes every mutation, including modules owning several guns.
        modules.put(info.id(),new Registered(info,module));
        for(String gun:info.gunIds())owners.put(gun,info);
        return this;
    }
    public synchronized List<ModuleInfo> snapshot() {
        return modules.values().stream().map(Registered::info).toList();
    }
    public synchronized Optional<ModuleInfo> ownerOf(String gunId) {
        return Optional.ofNullable(owners.get(gunId));
    }
    public synchronized State state() { return state; }

    /**
     * Success is idempotent. An in-progress or failed attempt cannot be restarted, because
     * constructors may already have registered irreversible listeners, attributes or packets.
     * Initialization runs outside the monitor so read-only queries and concurrent callers do
     * not block behind module code; concurrent/reentrant initialization fails immediately.
     */
    public void initialize() {
        List<Registered> pending;
        synchronized(this) {
            if(state==State.READY)return;
            if(state==State.FAILED)throw new IllegalStateException("Weapon module "+failedModule+" failed; initialization cannot be retried",failure);
            if(state==State.INITIALIZING)throw new IllegalStateException("Weapon modules are already initializing");
            state=State.INITIALIZING;pending=List.copyOf(modules.values());
        }
        for(var registered:pending) {
            try { registered.implementation().initialize(); }
            catch(Throwable error) {
                synchronized(this) { failure=error;failedModule=registered.info().id();state=State.FAILED; }
                if(error instanceof RuntimeException runtime)throw runtime;
                if(error instanceof Error fatal)throw fatal;
                throw new IllegalStateException("Weapon module "+registered.info().id()+" failed",error);
            }
        }
        synchronized(this) { state=State.READY; }
    }
}
