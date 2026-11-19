package cn.ssjjdark;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/** The creative-stone settlement bypasses LivingHurtEvent, so apply the active
 * session's fixed outgoing bonus once at its own damage entry. */
public final class DarkUltimateDamage {
    private DarkUltimateDamage(){}
    public static float scale(DamageSource source,float amount){
        if(source==null||!(source.getEntity() instanceof ServerPlayer player)||!DarkLiberation.ultimateActive(player)
            ||!Float.isFinite(amount)||amount<=0)return amount;
        return (float)Math.min(Float.MAX_VALUE,(double)amount*(1+DarkCrystalConfig.number("ultimate_wrath_bonus")));
    }
}
