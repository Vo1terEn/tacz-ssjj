package cn.fusionarsenal;

import cn.fusionarsenal.config.ArsenalConfig;
import cn.fusionarsenal.health.DeepHealthProbe;
import cn.fusionarsenal.health.DeathProgressProbe;
import cn.judgfire.BarrierHealth;
import cn.judgfire.CreativeHealthBridge;
import cn.ssjjares.AresMissileDamage;
import cn.ssjjdark.DarkLiberationGuard;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A committed health ceiling with a terminal latch. Never removes entities or creates loot. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class CreativeHealthState {
    private static final int RETENTION_TICKS = 100;

    static final class State {
        float health;
        final long tick;
        boolean terminal, deathStarted, nativeFallback, settledEver, opaque, unknown;
        int depth, deathClock, stalled;
        Map<String,Double> deathCounters=Map.of();
        WeakReference<DamageSource> source;
        boolean fixedMissile;
        final HashSet<String> deathMethods = new HashSet<>();
        State(float health, long tick) { this.health=health; this.tick=tick; terminal=health<=0; }
    }
    private static final Map<LivingEntity,State> STATES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<LivingEntity> RESTORE=new ThreadLocal<>();
    private static final ThreadLocal<LivingEntity> RAW_READ=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CLIENT_WRITE=new ThreadLocal<>();
    public static final LongAdder COMMITS=new LongAdder(), DEATHS=new LongAdder(), DUPLICATES=new LongAdder(), FALLBACKS=new LongAdder(), UNKNOWN_COMMITS=new LongAdder();
    private CreativeHealthState() {}
    static boolean enabled() { return ArsenalConfig.enabled("combat.creative_stone_enhanced_attack"); }
    static State state(LivingEntity target) { return STATES.get(target); }

    static void commit(LivingEntity target,float health) {
        health = DarkLiberationGuard.health(target, health);
        State old=STATES.get(target);
        if(old!=null&&old.terminal&&!old.unknown) return;
        STATES.put(target,new State(health,target.level().getGameTime()));
        COMMITS.increment();
    }
    static void opaque(LivingEntity target,boolean value){State state=STATES.get(target);if(state!=null)state.opaque=value;}
    static void arm(LivingEntity target, DamageSource source) {
        State state = STATES.get(target);
        if (state == null) return;
        state.source = new WeakReference<>(source);
        state.fixedMissile = source instanceof AresMissileDamage;
    }

    private static boolean reconcile(State state) {
        return state.fixedMissile || enabled();
    }
    public static boolean terminal(LivingEntity target) {
        State state=STATES.get(target);
        return state!=null&&state.terminal&&!state.unknown;
    }
    public static float ceiling(LivingEntity target,float physical) {
        State state=STATES.get(target);
        if(state==null||state.unknown||RESTORE.get()==target||RAW_READ.get()==target) return physical;
        if(state.terminal) return 0;
        if(!reconcile(state)) return physical;
        // A brief committed window also defeats direct-field / map restores that bypass setters.
        // Thereafter normal healing resumes; terminal latches persist until real removal/revival.
        if(target.level().getGameTime()-state.tick>RETENTION_TICKS) return physical;
        return Float.isFinite(physical)?Math.min(physical,state.health):state.health;
    }
    public static float request(LivingEntity target,float requested) {
        State state=STATES.get(target);
        if(state==null||state.unknown||RESTORE.get()==target||RAW_READ.get()==target||Boolean.TRUE.equals(CLIENT_WRITE.get())) return requested;
        if(state.terminal) return 0;
        if(reconcile(state)&&target.level().getGameTime()-state.tick<=RETENTION_TICKS) {
            // Only a completed pool write can lower the ceiling. An input to setHealth
            // can still be changed or reduced by the defense boundary.
            float stored = CreativeHealthBridge.read(target);
            if (Float.isFinite(stored) && stored >= 0 && stored < state.health) {
                state.health = stored;
                state.terminal = stored == 0;
            }
            if(!Float.isFinite(requested))return state.health;
            return Math.min(requested,state.health);
        }
        return requested;
    }
    public static boolean life(LivingEntity target,boolean original,boolean dying) {
        if(RAW_READ.get()==target)return original;
        if(CreativeDamage.trackingAlive(target))return !dying;
        return terminal(target)&&RESTORE.get()!=target ? dying : original;
    }
    static void physicalReads(LivingEntity target,Runnable action) {
        LivingEntity previous=RAW_READ.get();RAW_READ.set(target);
        try{action.run();}finally{if(previous==null)RAW_READ.remove();else RAW_READ.set(previous);}
    }
    static void uncertain(LivingEntity target,State previous){
        State pending=STATES.get(target);
        if(pending!=null&&pending.deathStarted)pending.unknown=true;
        else if(previous==null)STATES.remove(target);else STATES.put(target,previous);
    }
    private static boolean writePhysical(LivingEntity target,State state){
        DeepHealthProbe.Commit[] result={null};
        physicalReads(target, () -> BarrierHealth.commitResolved(target, state.health,
            () -> result[0] = DeepHealthProbe.commit(target, state.health)));
        if(result[0].state()==DeepHealthProbe.CommitState.UNKNOWN){state.unknown=true;UNKNOWN_COMMITS.increment();return false;}
        CreativeHealthBridge.write(target,state.health);return true;
    }
    static void restoring(LivingEntity target,Runnable action) {
        LivingEntity previous=RESTORE.get();RESTORE.set(target);
        try { action.run(); } finally { if(previous==null)RESTORE.remove();else RESTORE.set(previous); }
    }
    static void revive(LivingEntity target,float health) {
        if(!(health>0)||!Float.isFinite(health)) return;
        STATES.remove(target);
        restoring(target,()-> { DeepHealthProbe.write(target,health);CreativeHealthBridge.write(target,health); });
        CreativeHealthSync.release(target);
    }
    public static boolean enterDeath(LivingEntity target,String method) {
        State state=STATES.get(target);
        if(state==null||!state.terminal||RESTORE.get()==target) return true;
        // Different override/super methods may delegate once; recursion or another
        // top-level die call cannot repeat rewards or death events.
        if(state.deathMethods.contains(method)||state.depth==0&&state.deathStarted&&!state.nativeFallback) {
            DUPLICATES.increment(); return false;
        }
        state.deathMethods.add(method);state.depth++;state.deathStarted=true;
        var source = state.source == null ? null : state.source.get();
        if (source != null) CreativeHealthBridge.remember(target, source);
        if(state.depth==1)DEATHS.increment();
        return true;
    }
    public static void exitDeath(LivingEntity target,String method) {
        State state=STATES.get(target);
        if(state!=null&&state.terminal&&state.depth>0){
            if(CreativeHealthBridge.settled(target)){state.settledEver=true;target.getPersistentData().putBoolean("ssjjrpg_creative_settled",true);}
            state.depth--;
        }
    }
    static void fallbackDeath(LivingEntity target,DamageSource source) {
        State state=STATES.get(target);if(state==null||!state.terminal)return;
        if(CreativeHealthBridge.settled(target)){state.settledEver=true;return;}
        if(state.settledEver){CreativeHealthBridge.markSettled(target);return;}
        state.nativeFallback=true;
        try { CreativeHealthBridge.nativeDie(target,source);FALLBACKS.increment(); }
        finally { state.nativeFallback=false; }
    }
    public static void client(LivingEntity target,float health,boolean terminal) {
        if(!target.level().isClientSide||!Float.isFinite(health)||health<0)return;
        State old=STATES.get(target);
        if(old!=null&&old.terminal&&!terminal)STATES.remove(target);
        State received=new State(health,target.level().getGameTime());received.opaque=true;STATES.put(target,received);
        CLIENT_WRITE.set(true);
        try { DeepHealthProbe.write(target,health);CreativeHealthBridge.write(target,health); }
        finally { CLIENT_WRITE.remove(); }
    }
    public static void releaseClient(LivingEntity target,float health){
        if(!target.level().isClientSide)return;
        STATES.remove(target);CLIENT_WRITE.set(true);
        try{DeepHealthProbe.write(target,health);CreativeHealthBridge.write(target,health);}
        finally{CLIENT_WRITE.remove();}
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        LivingEntity[] targets;
        synchronized(STATES){
            if (STATES.isEmpty()) return;
            targets=STATES.keySet().toArray(LivingEntity[]::new);
        }
        for(LivingEntity target:targets){
            if(target==null||target.level()!=level)continue;
            State state=STATES.get(target);if(state==null)continue;
            if(target.isRemoved()){STATES.remove(target);continue;}
            if(!reconcile(state)&&!state.terminal&&!state.deathStarted){
                STATES.remove(target);CreativeHealthSync.release(target);continue;
            }
            if(state.unknown)continue;
            if(!state.terminal){
                if(level.getGameTime()-state.tick>RETENTION_TICKS){STATES.remove(target);CreativeHealthSync.release(target);continue;}
                float observed=DeepHealthProbe.read(target,CreativeHealthBridge.read(target));
                if(Float.isFinite(observed)&&observed>=0&&observed<state.health)state.health=observed;
                if(!writePhysical(target,state))continue;
                if(level.getGameTime()%10==0)CreativeHealthSync.send(target,state.health,false);
                continue;
            }
            // Counter raw private-field restoration as well as setter-based restoration.
            if(!writePhysical(target,state))continue;
            target.invulnerableTime=0;
            if(state.settledEver&&!CreativeHealthBridge.settled(target))CreativeHealthBridge.markSettled(target);
            int observed=target.deathTime;
            Map<String,Double> counters=DeathProgressProbe.sample(target);
            boolean customProgress=false;
            for(var counter:counters.entrySet()){
                Double prior=state.deathCounters.get(counter.getKey());
                if(prior!=null&&counter.getValue()>prior)customProgress=true;
            }
            state.deathCounters=counters;
            if(observed>state.deathClock||customProgress){state.deathClock=Math.max(state.deathClock,observed);state.stalled=0;}
            else state.stalled++;
            if((state.stalled>=100||level.getGameTime()-state.tick>=1200)&&CreativeHealthBridge.settled(target)){
                // Run the original native death tick when an override resets/omits it.
                // Its own removal rule remains responsible for removing the corpse.
                target.deathTime=state.deathClock;
                CreativeHealthBridge.nativeDeathTick(target);
                state.deathClock=Math.max(state.deathClock,target.deathTime);
            }
            if(level.getGameTime()%10==0)CreativeHealthSync.send(target,0,true);
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(event.getEntity() instanceof LivingEntity living)STATES.remove(living);}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){STATES.clear();RESTORE.remove();RAW_READ.remove();CLIENT_WRITE.remove();}
}
