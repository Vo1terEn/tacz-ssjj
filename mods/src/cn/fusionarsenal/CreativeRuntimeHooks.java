package cn.fusionarsenal;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Object-only API called by the Forge loading transformer; no JVM instrumentation. */
public final class CreativeRuntimeHooks {
    private CreativeRuntimeHooks() {}
    public static int hurt(Object target, Object source, float amount) {
        if (!(target instanceof LivingEntity living) || !(source instanceof DamageSource damage)
            || !CreativeDamage.eligible(living, damage)) return -1;
        return CreativeDamage.attack(living, damage, amount) ? 1 : 0;
    }
    public static float health(Object target, float original) {
        return target instanceof LivingEntity living ? CreativeDamage.readHealth(living, original) : original;
    }
    public static float request(Object target, float requested) {
        return target instanceof LivingEntity living ? CreativeHealthState.request(living, requested) : requested;
    }
    public static boolean life(Object target, boolean original, boolean dying) {
        return target instanceof LivingEntity living ? CreativeHealthState.life(living, original, dying) : original;
    }
    public static boolean deathResult(Object target, boolean canceled) {
        return target instanceof LivingEntity living && CreativeHealthState.terminal(living) ? false : canceled;
    }
    public static boolean enterDeath(Object target, Object source, String method) {
        return !(target instanceof LivingEntity living) || CreativeHealthState.enterDeath(living, method);
    }
    public static void exitDeath(Object target, String method) {
        if (target instanceof LivingEntity living) CreativeHealthState.exitDeath(living, method);
    }
}

