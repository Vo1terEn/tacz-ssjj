package cn.ssjjares;

import cn.fusionarsenal.CreativeDamage;
import cn.ssjjares.AresAccessoryConfig.Stat;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/** Source for a shoulder cannon hit, with fixed damage and native kill attribution. */
public final class AresMissileDamage extends DamageSource {
    private AresMissileDamage(Holder<DamageType> type, AresMissile missile, LivingEntity owner) {
        super(type, missile, owner);
    }

    static void hit(AresMissile missile, LivingEntity owner, LivingEntity target) {
        var type = owner.damageSources().mobProjectile(missile, owner).typeHolder();
        CreativeDamage.attackFixed(target, new AresMissileDamage(type, missile, owner),
            amount(target.getMaxHealth(), missile.advanced() ? 2 : 1));
    }

    static float amount(float maxHealth) {
        return amount(maxHealth, 1);
    }

    static float amount(float maxHealth, int tier) {
        int kind = AresAccessoryConfig.SHOULDER;
        return (float) Stat.MISSILE_BASE.number(kind, tier)
            + maxHealth * (float) (Stat.MISSILE_HEALTH.number(kind, tier) / 100);
    }
}
