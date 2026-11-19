package cn.ssjjdark;

/** Inclusive ten-percent health thresholds and additive per-layer Absolute Wrath bonuses. */
public final class DarkWrathRules {
    private DarkWrathRules(){}
    public static int tier(String namespace,String path){
        if(!"ssjj_dark".equals(namespace))return 0;
        return "darkdiamond1".equals(path)?1:"darkdiamond2".equals(path)?2:0;
    }
    /** A float exactly on a threshold retains its layer; the next representable HP above it does not. */
    public static int layers(float health,float maximum){
        if(!Float.isFinite(health)||!Float.isFinite(maximum)||maximum<=0||health<=0||health>=maximum)return 0;
        int result=0;
        for(int layer=1;layer<=9;layer++){
            float threshold=(float)((double)maximum*(10-layer)/10);
            if(health<=threshold)result=layer;
        }
        return result;
    }
    public static double damagePerLayer(int tier,boolean enhanced){
        return tier==1||tier==2?DarkCrystalConfig.number("wrath_damage_per_layer")*(enhanced?DarkCrystalConfig.number("crystal_blade_multiplier"):1):0;
    }
    public static double reductionPerLayer(int tier,boolean enhanced){
        return tier==2?DarkCrystalConfig.number("wrath_reduction_per_layer")*(enhanced?DarkCrystalConfig.number("crystal_blade_multiplier"):1):0;
    }
}
