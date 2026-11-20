package cn.fusionarsenal;

import cn.fusionarsenal.config.ArsenalConfig;
import cn.fusionarsenal.health.DeepHealthProbe;
import cn.judgfire.BarrierHealth;
import cn.judgfire.CreativeHealthBridge;
import cn.judgfire.EnergyAttributes;
import cn.ssjjares.AresCombat;
import cn.ssjjares.AresMissileDamage;
import cn.ssjjdark.DarkLiberationBypass;
import cn.ssjjdark.DarkLiberationGuard;
import cn.ssjjdark.DarkUltimateDamage;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-only creative stone health commit. Death, loot and kill credit remain native. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class CreativeDamage {
    private record Shot(double strength) {}
    private static final Map<EntityKineticBullet, Shot> SHOTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<LivingEntity> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Float> TRACKING_HEALTH = new ThreadLocal<>();
    private CreativeDamage() {}
    static boolean trackingAlive(LivingEntity target){return ACTIVE.get()==target&&TRACKING_HEALTH.get()!=null&&TRACKING_HEALTH.get()>0;}

    private static boolean enabled() { return ArsenalConfig.enabled("combat.creative_stone_enhanced_attack"); }

    public static void capture(EntityKineticBullet bullet, ItemStack weapon) {
        if (!(bullet.level() instanceof ServerLevel))return;
        if (CreativeStone.installed(weapon)) {
            double strength = bullet.getOwner() instanceof LivingEntity owner ? EnergyAttributes.strength(owner) : 0;
            SHOTS.put(bullet, new Shot(strength));
        } else SHOTS.put(bullet,new Shot(Double.NaN));
    }

    public static boolean bulletEligible(EntityKineticBullet bullet) {
        Shot snapshot=SHOTS.get(bullet);
        return enabled() && bullet.level() instanceof ServerLevel && snapshot!=null&&!Double.isNaN(snapshot.strength());
    }

    public static void inherit(EntityKineticBullet copy, EntityKineticBullet source) {
        Shot snapshot = SHOTS.get(source);
        if (snapshot != null) SHOTS.put(copy, snapshot);
    }
    public static void freeze(Object projectile){
        if(projectile instanceof EntityKineticBullet bullet&&!SHOTS.containsKey(bullet)&&bullet.getOwner() instanceof LivingEntity owner)
            capture(bullet,owner.getMainHandItem());
    }

    /** Targeted call-site bridge for this pack's skills, including foreign hurt overrides. */
    public static boolean route(Entity part, DamageSource source, float amount) {
        if (part == null) return false;
        Entity core = CreativeTargets.resolve(part);
        if (core instanceof LivingEntity target && eligible(target, source)) return attack(core, source, amount);
        return part.hurt(source, amount);
    }

    public static boolean filterCustom(Entity part, boolean original, EntityKineticBullet bullet) {
        return original && !(bulletEligible(bullet)
            && CreativeTargets.resolve(part) instanceof LivingEntity);
    }

    public static boolean eligible(LivingEntity target, DamageSource source) {
        if (!enabled() || ACTIVE.get() != null || !(target.level() instanceof ServerLevel) || source == null) return false;
        if (source.getDirectEntity() instanceof EntityKineticBullet bullet) return bulletEligible(bullet);
        return source.getEntity() instanceof LivingEntity owner && CreativeStone.installed(owner.getMainHandItem());
    }

    /** Let every listener run, then remove cancellation/capping only for this captured shot. */
    public static boolean post(IEventBus bus, Event event) {
        float original = snapshotPreDamage(event);
        return finishPreDamage(original, event, bus.post(event));
    }

    public static float snapshotPreDamage(Event event) {
        if (!(event instanceof EntityHurtByGunEvent.Pre pre)
            || !(pre.getBullet() instanceof EntityKineticBullet bullet) || !bulletEligible(bullet)) return Float.NaN;
        return pre.getBaseAmount();
    }

    public static boolean finishPreDamage(float original, Event event, boolean canceled) {
        if (!Float.isFinite(original) || original <= 0) return canceled;
        EntityHurtByGunEvent.Pre pre = (EntityHurtByGunEvent.Pre) event;
        float modified = pre.getBaseAmount();
        pre.setBaseAmount(Float.isFinite(modified) ? Math.max(original, modified) : original);
        pre.setCanceled(false);
        return false;
    }

    public static boolean bullet(EntityKineticBullet bullet, Entity part, float amount, DamageSource source) {
        if (!bulletEligible(bullet)) return false;
        Entity core = CreativeTargets.resolve(part);
        if (!(core instanceof LivingEntity target)) return false;
        if (source == null || source.getEntity() != bullet.getOwner()) return false;
        // True means this component was handled, including zero damage: do not fall through to AP twice.
        apply(target, source, amount, SHOTS.get(bullet).strength(), false);
        return true;
    }

    public static boolean attack(Entity part, DamageSource source, float amount) {
        if (part == null || source == null) return false;
        // Preserve direct callers that already pass the shoulder missile marker.
        if (source instanceof AresMissileDamage missile) {
            return part instanceof LivingEntity target && attackFixed(target, missile, amount);
        }
        Entity core = CreativeTargets.resolve(part);
        if (!(core instanceof LivingEntity target) || !eligible(target, source)) return false;
        double strength = source.getDirectEntity() instanceof EntityKineticBullet bullet && SHOTS.containsKey(bullet)
            ? SHOTS.get(bullet).strength() : source.getEntity() instanceof LivingEntity owner ? EnergyAttributes.strength(owner) : 0;
        if (source instanceof cn.ssjjparasite2.client.ParasiteDestructionDamage) strength = 0;
        return apply(target, source, amount, strength, false);
    }

    /** Commit the shoulder cannon's fixed damage while preserving native death settlement. */
    public static boolean attackFixed(LivingEntity target, AresMissileDamage source, float amount) {
        return target != null && source != null && apply(target, source, amount, 0, true);
    }

    private static float amount(float base, double strength) {
        if (!Float.isFinite(base) || base <= 0) return 0;
        double coefficient = ArsenalConfig.number("combat.strength_damage_per_1000");
        double multiplier = Double.isFinite(strength) && strength > 0 && Double.isFinite(coefficient) && coefficient > 0
            ? 1 + strength / 1000 * coefficient : 1;
        return (float)Math.min(Float.MAX_VALUE, (double)base * multiplier);
    }

    private static boolean apply(LivingEntity target, DamageSource source, float base, double strength, boolean fixed) {
        try (var scope = DarkLiberationBypass.open(target)) {
            float outgoing = fixed ? base : DarkUltimateDamage.scale(source, base);
            return settle(target, source, outgoing, strength, fixed);
        }
    }

    private static boolean settle(LivingEntity target, DamageSource source, float base, double strength, boolean fixed) {
        if (DarkLiberationGuard.blocked(target)) return true;
        if (!(target.level() instanceof ServerLevel level) || ACTIVE.get() != null || target.isRemoved() || target.isSpectator()) return false;
        if(CreativeHealthState.terminal(target))return false;
        float raw = fixed ? base : AresCombat.scaleDirect(target, source, amount(base, strength));
        if (!Float.isFinite(raw) || raw <= 0) return false;
        float damage = BarrierHealth.defendDirect(target, source, raw, fixed);
        if (damage <= 0) return true;
        float before = physical(target);
        if (!Float.isFinite(before) || before <= 0) return false;
        float after = Math.max(0, before - damage);
        if (after == before) {
            if (damage < raw) return true;
            after = Math.max(0, Math.nextDown(before));
        }
        final float committedHealth = after;
        LivingEntity previous = ACTIVE.get();
        ACTIVE.set(target);
        try {
            DeepHealthProbe.Commit[] transaction = {null};
            CreativeHealthState.State previousState = CreativeHealthState.state(target);
            CreativeHealthState.commit(target, after);
            CreativeHealthState.arm(target, source);
            // Match native order and record combat while the physical pool is still alive.
            CreativeHealthState.physicalReads(target, () -> {
                CreativeHealthBridge.remember(target, source);
                TRACKING_HEALTH.set(before);
                try { target.getCombatTracker().recordDamage(source, Math.min(before, damage)); }
                finally { TRACKING_HEALTH.remove(); }
            });
            CreativeHealthState.physicalReads(target, () -> BarrierHealth.commitResolved(target, committedHealth,
                () -> transaction[0] = DeepHealthProbe.commit(target, committedHealth)));
            if (transaction[0].state() == DeepHealthProbe.CommitState.UNKNOWN) {
                CreativeHealthState.UNKNOWN_COMMITS.increment();
                CreativeHealthState.uncertain(target, previousState);
                return true; // An unknown transaction may already have changed storage.
            }
            boolean confirmed = transaction[0].state() == DeepHealthProbe.CommitState.CONFIRMED_COMMIT;
            CreativeHealthState.opaque(target, !confirmed);
            CreativeHealthBridge.write(target, after);
            target.invulnerableTime = 0;
            target.hurtDuration = 10;
            target.hurtTime = 10;
            if (after <= 0) {
                // Virtual dispatch deliberately preserves boss death phases and Forge LivingDeathEvent.
                // A death-event revival is respected; no discard(), remove() or entity-map mutation.
                boolean[] protectedDeath={false};
                CreativeHealthState.restoring(target,()->protectedDeath[0]=CreativeHealthBridge.totem(target,source));
                float revived = CreativeHealthBridge.read(target);
                if(protectedDeath[0]&&revived>0){CreativeHealthState.revive(target,revived);return true;}
                target.die(source);
                CreativeHealthState.fallbackDeath(target,source);
                if(CreativeHealthBridge.settled(target))target.getPersistentData().putBoolean("ssjjrpg_creative_settled",true);
            } else {
                level.broadcastEntityEvent(target, (byte)2);
            }
            CreativeHealthSync.send(target,after,after<=0);
            return true;
        } finally {
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }

    private static float physical(LivingEntity target) {
        float vanilla = CreativeHealthBridge.read(target);
        float visible=target.getHealth();
        float fallback=Float.isFinite(visible)&&visible>0?visible:vanilla;
        return CreativeHealthState.ceiling(target,DeepHealthProbe.read(target,fallback));
    }

    /** Correct native death checks even when a virtual getter returns an artificial health value. */
    public static float readHealth(LivingEntity target, float returned) {
        Float tracking = TRACKING_HEALTH.get();
        if (ACTIVE.get() == target && tracking != null) return tracking;
        return CreativeHealthState.ceiling(target,returned);
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityKineticBullet bullet) SHOTS.remove(bullet);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide)return;
        if(event.getEntity() instanceof EntityKineticBullet bullet&&!SHOTS.containsKey(bullet)&&bullet.getOwner() instanceof LivingEntity owner)
            capture(bullet,owner.getMainHandItem());
        if(event.getEntity() instanceof LivingEntity target&&target.getPersistentData().getBoolean("ssjjrpg_creative_settled")){
            CreativeHealthState.commit(target,0);CreativeHealthBridge.write(target,0);CreativeHealthBridge.markSettled(target);
            CreativeHealthSync.send(target,0,true);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SHOTS.clear(); ACTIVE.remove(); TRACKING_HEALTH.remove(); }
}

