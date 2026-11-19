package cn.ssjjdark;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Explicit trusted-code escape hatch for Absolute Liberation invulnerability.
 *
 * This is scoped to one concrete entity on the current thread. It does not
 * disable shields: the called attack/write path retains its usual shield and
 * settlement semantics. Creative Stone never opens this scope automatically.
 * Creative Stone's authenticated attack settlement explicitly opens this scope.
 * Ordinary restore flags and entity NBT never grant this bypass.
 */
public final class DarkLiberationBypass {
    private static final ThreadLocal<IdentityHashMap<LivingEntity,Integer>> SCOPES=new ThreadLocal<>();
    private DarkLiberationBypass(){}

    /** True only inside an explicit run/call scope for this exact entity. */
    public static boolean allowed(LivingEntity target){
        var scopes=SCOPES.get();
        return target!=null&&scopes!=null&&scopes.containsKey(target);
    }

    /** The finally block restores nested scopes even if the trusted action throws. */
    public static void run(LivingEntity target,Runnable action){
        Objects.requireNonNull(action,"action");
        call(target,()->{action.run();return null;});
    }

    /** Opens a target-specific scope for trusted settlement code; close on the calling thread. */
    public static Scope open(LivingEntity target){
        Objects.requireNonNull(target,"target");
        var scopes=SCOPES.get();
        if(scopes==null){scopes=new IdentityHashMap<>();SCOPES.set(scopes);}
        Integer previous=scopes.get(target);
        scopes.put(target,previous==null?1:Math.addExact(previous,1));
        return new Scope(target);
    }
    public static final class Scope implements AutoCloseable {
        private final LivingEntity target;
        private final Thread owner=Thread.currentThread();
        private boolean closed;
        private Scope(LivingEntity target){this.target=target;}
        @Override public void close(){
            if(closed)return;
            if(Thread.currentThread()!=owner)throw new IllegalStateException("Bypass scope closed on another thread");
            closed=true;
            var scopes=SCOPES.get();
            if(scopes==null||!scopes.containsKey(target))throw new IllegalStateException("Missing bypass scope");
            int depth=scopes.get(target);
            try{
                // Reconcile after the actual write/rollback, while this target is
                // still authorized. Inner scopes leave this to their outer scope.
                if(depth==1&&target instanceof ServerPlayer player)
                    DarkLiberation.reconcileBypassHealth(player);
            }finally{
                if(depth==1)scopes.remove(target);else scopes.put(target,depth-1);
                if(scopes.isEmpty())SCOPES.remove();
            }
        }
    }
    /** Returns the action result; exceptions always close the target's scope. */
    public static <T> T call(LivingEntity target,Supplier<T> action){
        Objects.requireNonNull(action,"action");
        try(Scope scope=open(target)){return action.get();}
    }
}
