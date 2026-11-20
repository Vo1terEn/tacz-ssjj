package cn.ssjjdark;

import net.minecraft.world.damagesource.DamageSource;

/** Bridge for creative-stone damage that settles outside LivingHurtEvent. */
public final class DarkUltimateDamage {
    private DarkUltimateDamage(){}
    public static float scale(DamageSource source,float amount){
        return DarkWrath.scaleOutgoing(source,amount);
    }
}
