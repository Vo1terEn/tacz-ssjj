package cn.ssjjdark;

/** Pure timing, sacrifice and shield arithmetic; all durations use server ticks. */
public final class DarkLiberationRules {
    public static final int COOLDOWN=120,CHARGE=60,DURATION=360,CYCLE=DURATION+COOLDOWN;
    public static final int COOLDOWN_TICKS=COOLDOWN,CHARGE_TICKS=CHARGE,DURATION_TICKS=DURATION;
    public static final float HEALTH_FRACTION=.70f,EXPLOSION_DAMAGE=30;
    public static final double SPEED_BONUS=.20,RADIUS=6;
    private DarkLiberationRules(){}
    public static int invulnerability(boolean core){return core?200:100;}
    public static float shieldFactor(boolean core){return (float)DarkCrystalConfig.number(core?"core_shield_capacity_multiplier":"shield_capacity_multiplier");}
    public static float sacrificeShieldFactor(boolean core){return (float)DarkCrystalConfig.number(core?"core_sacrifice_shield_multiplier":"sacrifice_shield_multiplier");}
    public static double speedBonus(){return DarkCrystalConfig.number("liberation_speed_bonus");}
    public static double radius(){return DarkCrystalConfig.number("liberation_blast_radius");}
    public static float explosionDamage(){return (float)DarkCrystalConfig.number("liberation_blast_damage");}
    public static int remaining(long now,long until,int maximum){
        if(now<0||until<=now)return 0;
        long difference=until-now;
        return difference<0?maximum:(int)Math.min(maximum,difference);
    }
    public static float sacrifice(float health){
        return Float.isFinite(health)&&health>0?health*(float)DarkCrystalConfig.number("liberation_health_cost_fraction"):0;
    }
    public static float survivingHealth(float health){
        if(!Float.isFinite(health)||health<=0)return 0;
        return Math.max(Float.MIN_VALUE,health-sacrifice(health));
    }
    public static float castHealth(float health,boolean ultimate){return ultimate?1:survivingHealth(health);}
    public static float endingHealth(float maxHealth){
        float fraction=(float)DarkCrystalConfig.number("ending_health_fraction");
        return Float.isFinite(maxHealth)&&maxHealth>0&&fraction>0?Math.max(Float.MIN_VALUE,maxHealth*fraction):0;
    }
    public static float capacity(float maxHealth,boolean core){
        if(!Float.isFinite(maxHealth)||maxHealth<=0)return 0;
        return (float)Math.min(Float.MAX_VALUE,(double)maxHealth*shieldFactor(core));
    }
    public static float add(float shield,double amount,float capacity){
        if(!Float.isFinite(capacity)||capacity<=0)return 0;
        double current=Float.isFinite(shield)?Math.max(0,Math.min(capacity,shield)):0;
        if(Double.isNaN(amount)||amount<=0)return (float)current;
        return (float)Math.min(capacity,current+amount);
    }
    public record Boundary(float health,float shield){}
    /** Exact health boundary; no health reads and no second damage-event settlement. */
    public static Boundary boundary(float old,float adjusted,float shield,float capacity,boolean invulnerable){
        float pool=add(shield,0,capacity);
        if(!Float.isFinite(old)||old<=0)return new Boundary(adjusted,pool);
        if(Float.isNaN(adjusted))return new Boundary(old,pool);
        if(adjusted>old)return new Boundary(old,add(pool,(double)adjusted-old,capacity));
        if(adjusted==old)return new Boundary(adjusted,pool);
        if(invulnerable)return new Boundary(old,pool);
        double loss=(double)old-adjusted,absorbed=Math.min(pool,loss);
        return new Boundary((float)(adjusted+absorbed),(float)Math.max(0,pool-absorbed));
    }
}



