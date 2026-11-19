package cn.ssjjdark;

/** Tier selection and travel constants for Dragon Seal Slash on both attack actions. */
public final class DarkDragonRules {
    public static final double RANGE=18.0,SPEED=.6,RADIUS=.4375,SPREAD_DEGREES=18.0,EPSILON=1e-7;
    public static final int MAX_AGE=(int)Math.ceil(RANGE/SPEED)+1;
    private DarkDragonRules(){}
    public static int tier(String namespace,String path){
        if(!"ssjj_dark".equals(namespace))return 0;
        return "darkdiamond1".equals(path)?1:"darkdiamond2".equals(path)?2:0;
    }
    public static int count(int tier){return tier==1?1:tier==2?3:0;}
    public static float damage(int tier){return tier==1?(float)DarkCrystalConfig.number("dragon_tier1_damage"):tier==2?(float)DarkCrystalConfig.number("dragon_tier2_damage"):0;}
    public static double angleDegrees(int tier,int index){
        int count=count(tier);
        if(index<0||index>=count)throw new IllegalArgumentException("Invalid dragon wave index");
        return count==1?0:(index-1)*SPREAD_DEGREES;
    }
    public static double step(double distance){
        if(!Double.isFinite(distance)||distance<0||distance>=RANGE)return 0;
        return Math.min(SPEED,RANGE-distance);
    }
}
