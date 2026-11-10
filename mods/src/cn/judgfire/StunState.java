package cn.judgfire;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Entity-local server state: no potion instances and no cross-side entity caches. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class StunState {
    static final String UNTIL = "JudgfireStunUntilV2";
    static final String SAVED = "JudgfireStunOriginalNoAI";
    static final String CLIENT = "JudgfireStunClientUntilV2";
    static final UUID SPEED = UUID.fromString("9854618e-8aba-4a4a-a70b-c028e480ce9d");
    private StunState() {}

    static long now(LivingEntity e) { return e.level().getGameTime(); }
    public static boolean stunned(LivingEntity e) {
        if (e == null || !e.isAlive() || e.isRemoved()) return false;
        return e.getPersistentData().getLong(e.level().isClientSide() ? CLIENT : UNTIL) > now(e);
    }

    static boolean apply(LivingEntity e, int ticks, boolean refresh) {
        if (e == null || e.level().isClientSide() || !e.isAlive() || e.isRemoved() || ticks <= 0) return false;
        maintain(e);
        var data = e.getPersistentData();
        long time = now(e);
        if (!refresh && !BurstTiming.canStun(stunned(e), time, data.getLong(BurstStun.IMMUNE))) return false;
        long end = time + (long) ticks;
        if (end <= data.getLong(UNTIL)) return false;
        data.putLong(UNTIL, end);
        freeze(e);
        sync(e);
        return true;
    }

    private static void freeze(LivingEntity e) {
        var speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED) == null)
            speed.addTransientModifier(new AttributeModifier(SPEED, "SSJJ stun", -1, AttributeModifier.Operation.MULTIPLY_TOTAL));
        var velocity = e.getDeltaMovement();
        e.setDeltaMovement(0, Math.min(0, velocity.y), 0);
        if (e instanceof Mob mob) {
            var data = mob.getPersistentData();
            if (!data.contains(SAVED)) data.putBoolean(SAVED, mob.isNoAi());
            mob.setNoAi(true);
            mob.getNavigation().stop();
        }
    }

    private static void release(LivingEntity e, boolean immunity) {
        var data = e.getPersistentData();
        boolean hadState = data.contains(UNTIL) || data.contains(SAVED);
        data.remove(UNTIL);
        var speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED);
        if (e instanceof Mob mob && data.contains(SAVED)) mob.setNoAi(data.getBoolean(SAVED));
        data.remove(SAVED);
        if (immunity && hadState) data.putLong(BurstStun.IMMUNE, now(e) + (long) RedLotus.BURST_IMMUNITY.get());
        if (hadState) sync(e);
    }

    /** Also removes legacy effects and repairs a saved NoAI flag left by an old build. */
    static void maintain(LivingEntity e) {
        if (e.level().isClientSide()) return;
        var data = e.getPersistentData();
        MobEffectInstance old = e.getEffect(BurstStun.STUN.get());
        if (old != null) {
            int remaining = old.getDuration();
            e.removeEffect(BurstStun.STUN.get());
            if (remaining > 0 && e.isAlive()) data.putLong(UNTIL, Math.max(data.getLong(UNTIL), now(e) + (long) remaining));
        }
        if (stunned(e)) freeze(e);
        else if (data.contains(UNTIL) || data.contains(SAVED)) release(e, e.isAlive());
        else {
            // A prior version could save the old effect's movement modifier separately.
            var speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(SPEED) != null) speed.removeModifier(SPEED);
        }
        if (old != null) sync(e);
    }

    static void sync(LivingEntity e) {
        if (e instanceof ServerPlayer player) StunNetwork.send(player, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, e.getPersistentData().getLong(UNTIL) - now(e))));
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void tick(LivingEvent.LivingTickEvent event) {
        var e = event.getEntity();
        if (e.level().isClientSide()) return;
        maintain(e);
        if (stunned(e) && e.tickCount % 20 == 0) sync(e);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity e) maintain(e);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { maintain(event.getEntity()); sync(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { maintain(event.getEntity()); sync(event.getEntity()); }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) release(event.getEntity(), false);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        // Death/End-return clones must never inherit a control lock from the old entity.
        var e = event.getEntity();
        if (!e.level().isClientSide()) { release(e, false); e.getPersistentData().remove(CLIENT); }
    }
}
