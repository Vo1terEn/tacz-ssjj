package cn.judgfire;

import cn.fusionarsenal.config.ArsenalConfig;
import cn.ssjjparasite2.client.ParasiteDestructionDamage;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Changes only the native armour and enchantment reduction stages of a hit. */
public final class EnergyArmorPenetration {
    public static final String CONFIG="combat.penetration_ignore_damage_reduction";
    private EnergyArmorPenetration(){}

    /** 500 penetration removes all reduction; values below it remove a proportion. */
    public static double ratio(LivingEntity target,DamageSource source){
        if(target==null||target.level()==null||target.level().isClientSide||source==null
            ||source instanceof ParasiteDestructionDamage||!ArsenalConfig.bool(CONFIG))return 0;
        if(!(source.getEntity() instanceof LivingEntity attacker))return 0;
        double penetration=EnergyAttributes.penetration(attacker);
        if(!(penetration>0))return 0;
        return Math.min(1,penetration/500);
    }

    private static float restore(float incoming,float reduced,double ratio){
        if(!(ratio>0)||!Float.isFinite(incoming)||!Float.isFinite(reduced)||!(incoming>reduced))return reduced;
        return (float)Math.min(incoming,(double)reduced+((double)incoming-reduced)*ratio);
    }

    public static float armor(float incoming,float armor,float toughness,LivingEntity target,DamageSource source){
        return restore(incoming,CombatRules.getDamageAfterAbsorb(incoming,armor,toughness),ratio(target,source));
    }

    /** Incoming here has already passed resistance potions; that reduction stays intact. */
    public static float magic(float incoming,float protection,LivingEntity target,DamageSource source){
        return restore(incoming,CombatRules.getDamageAfterMagicAbsorb(incoming,protection),ratio(target,source));
    }

    /** Called after the original stage, including other mods' redirects and formulas. */
    public static float afterArmor(float reduced,float incoming,LivingEntity target,DamageSource source){
        return restore(incoming,reduced,ratio(target,source));
    }

    /** Stage input has already passed resistance potions; never recompute protection. */
    public static float afterMagic(float reduced,float incoming,LivingEntity target,DamageSource source){
        return restore(incoming,reduced,ratio(target,source));
    }
}
