package cn.ssjjparasite2.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/** Replays settled damage once through native protections, without a second gun hit or energy pass. */
public final class ParasiteDestructionDamage extends DamageSource {
    public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
        new ResourceLocation("ssjjparasite2", "destruction"));

    public ParasiteDestructionDamage(LivingEntity target, LivingEntity shooter) {
        super(target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE), shooter, shooter);
    }

    @Override public boolean is(TagKey<DamageType> tag) {
        // These defenses already contributed to the recorded five losses. Native
        // invulnerability, creative protection, phase logic and Forge cancellation remain.
        return tag == DamageTypeTags.BYPASSES_ARMOR || tag == DamageTypeTags.BYPASSES_SHIELD
            || tag == DamageTypeTags.BYPASSES_COOLDOWN || tag == DamageTypeTags.BYPASSES_EFFECTS
            || tag == DamageTypeTags.BYPASSES_RESISTANCE || tag == DamageTypeTags.BYPASSES_ENCHANTMENTS
            || super.is(tag);
    }

    public static boolean apply(LivingEntity shooter, LivingEntity target, double settled) {
        float amount = ParasiteDestructionRules.nativeAmount(settled);
        if (amount <= 0 || !target.isAlive() || target.isRemoved() || !ParasiteShockwave.enemy(shooter, target)) return false;
        var source = new ParasiteDestructionDamage(target, shooter);
        // BarrierHealth snapshots this exact source with neutral energy, and the
        // EnergyScaling event guard skips only this class. Nested unrelated hits
        // keep their own original native scopes and attribute snapshots.
        // The core's default dragon hurt delegates to its body and would apply
        // the body's 1/4 modifier a second time. Head keeps phase/Forge handling.
        return target instanceof EnderDragon dragon ? dragon.head.hurt(source, amount) : target.hurt(source, amount);
    }
}
