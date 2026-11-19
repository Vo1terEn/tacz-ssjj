package cn.ssjjhyper;

/** Damage and energy units for the level-one Energy Core skill. */
public final class HyperCoreRules {
    public static final int MAX_ORBS=15;
    public static final int RETURN_IMPACT_TICKS=10,RETURN_DURATION_TICKS=18;
    private HyperCoreRules(){}
    public static int maxOrbs(){return HyperConfig.integer("core_max_orbs");}
    public static int cooldown(){return HyperConfig.integer("core_cooldown_ticks");}
    public static float capacity(boolean red,boolean enhanced){return (float)HyperConfig.number(enhanced?(red?"stock_red_capacity":"stock_blue_capacity"):(red?"core_red_capacity":"core_blue_capacity"));}
    public static int refund(boolean enhanced){return HyperConfig.integer(enhanced?"stock_refund_energy":"core_refund_energy");}
    public static float charge(float current,float damage,float cap){
        if(!Float.isFinite(current)||!Float.isFinite(damage)||!Float.isFinite(cap)||damage<=0||cap<=0)return current;
        return (float)Math.min(cap,Math.max(0,(double)current)+damage);
    }
    public static float absorb(float charge,float damage){
        return Float.isFinite(charge)&&Float.isFinite(damage)?Math.max(0,Math.min(charge,damage)):0;
    }
}
