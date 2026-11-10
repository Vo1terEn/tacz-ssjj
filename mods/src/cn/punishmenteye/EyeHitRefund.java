package cn.punishmenteye;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Confirms the existing lightning/cloud hit without changing damage or invulnerability time. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class EyeHitRefund {
    private static final ThreadLocal<Attempt> CURRENT = new ThreadLocal<>();

    private static final class Attempt {
        final LivingEntity target;
        final DamageSource source;
        LivingAttackEvent attack;
        LivingHurtEvent hurt;
        LivingDamageEvent damage;
        Attempt(LivingEntity target, DamageSource source) {
            this.target = target;
            this.source = source;
        }
    }

    private EyeHitRefund() {}

    /** Used only in the two existing NofaPower damage/refund expressions. One native hurt call. */
    public static boolean acceptedHit(LivingEntity target, DamageSource source, float amount) {
        boolean eligible = target.isAlive() && !target.isSpectator()
            && !(target instanceof Player player && player.isCreative())
            && !target.isInvulnerableTo(source) && Float.isFinite(amount) && amount > 0;
        double before = eligible ? pool(target) : Double.NaN;
        boolean cooldown = eligible && target.invulnerableTime > 10
            && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)
            && !source.is(DamageTypeTags.IS_FIRE)
            && !target.isDamageSourceBlocked(source);
        Attempt previous = CURRENT.get();
        Attempt attempt = new Attempt(target, source);
        CURRENT.set(attempt);
        try {
            boolean accepted = target.hurt(source, amount);
            // Keep the event objects, rather than an early canceled flag: later listeners
            // at this same priority may still cancel or change their amount.
            if (!eligible || canceled(attempt)) return false;
            double after = pool(target);
            boolean loss = Double.isFinite(before) && Double.isFinite(after) && after < before;
            boolean killed = before > 0 && !target.isAlive();
            if (attempt.hurt != null && attempt.hurt.getAmount() <= 0 && !loss && !killed) return false;
            if (attempt.damage != null && attempt.damage.getAmount() <= 0 && !loss && !killed) return false;
            if (accepted || loss || killed) return true;
            // Forge's LivingAttack hook precedes the native cooldown early return.
            // A blocked/immune/canceled attack, or a hit that reached actuallyHurt,
            // cannot use this fallback. In particular we never bypass its damage check.
            return cooldown && attempt.attack != null
                && attempt.hurt == null && attempt.damage == null;
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    private static double pool(LivingEntity target) {
        return (double) target.getHealth() + target.getAbsorptionAmount();
    }

    private static boolean canceled(Attempt attempt) {
        return attempt.attack != null && attempt.attack.isCanceled()
            || attempt.hurt != null && attempt.hurt.isCanceled()
            || attempt.damage != null && attempt.damage.isCanceled();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void attack(LivingAttackEvent event) {
        Attempt attempt = CURRENT.get();
        if (attempt != null && event.getEntity() == attempt.target && event.getSource() == attempt.source)
            attempt.attack = event;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void hurt(LivingHurtEvent event) {
        Attempt attempt = CURRENT.get();
        if (attempt != null && event.getEntity() == attempt.target && event.getSource() == attempt.source)
            attempt.hurt = event;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void damage(LivingDamageEvent event) {
        Attempt attempt = CURRENT.get();
        if (attempt != null && event.getEntity() == attempt.target && event.getSource() == attempt.source)
            attempt.damage = event;
    }
}
