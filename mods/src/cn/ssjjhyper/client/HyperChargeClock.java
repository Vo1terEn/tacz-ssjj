package cn.ssjjhyper.client;

import cn.ssjjhyper.HyperChargeRules;

/** Presentation only. Damage, energy, and cooldown authority stay on the server. */
public final class HyperChargeClock {
    private HyperChargeClock() {}
    public static int remaining(int snapshot,long received,long now) {
        return (int)Math.max(0,(long)snapshot-Math.max(0,now-received));
    }
    public static float energy(int value) {return Math.max(0,Math.min(1000,value))/1000f;}
    public static float duration(int snapshot,int total,long received,long now,float partial) {
        if(total<=0)return 0;
        float left=Math.max(0,remaining(snapshot,received,now)-Math.max(0,Math.min(1,partial)));
        return Math.max(0,Math.min(1,left/total));
    }
    /** Project the server's current energy snapshot to full at the remaining recovery deadline. */
    public static float recovery(int value,int ticks,long received,long now,float partial) {
        if(ticks<=0)return 1;
        float from=energy(value),elapsed=Math.max(0,now-received)+Math.max(0,Math.min(1,partial));
        return Math.max(from,Math.min(1,from+elapsed*HyperChargeRules.recoveryPerTick()/1000f));
    }
    /** Kept for cooldown timing consumers; the energy bar uses recovery instead. */
    public static float cooldown(int snapshot,long received,long now,float partial) {
        return duration(snapshot,HyperChargeRules.recoveryTicks(0),received,now,partial);
    }
    public static boolean bright(int activeTicks,int recoveryTicks){return activeTicks>0||recoveryTicks<=0;}
    public record Center(float x,float y,float scale) {}
    public static Center center(int width,int height,int tier) {
        float scale=Math.min(width/1920f,height/1080f);
        // Apply the exported SUI center offsets in Minecraft's screen coordinates.
        return new Center(width*.5f-(tier==2?90:89)*scale,height*.5f-136*scale,scale);
    }
}
