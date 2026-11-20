package cn.ssjjdark.client;

import cn.ssjjdark.DarkLiberationRules;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared source-pixel layout and authoritative-snapshot countdown arithmetic. */
public final class DarkLiberationHudModel {
    public record Layout(int x,int y,int width,int height,float scale){}
    public record Remaining(int charge,int active,int cooldown,int invulnerable){}
    private DarkLiberationHudModel(){}
    public static Layout layout(int width,int height){
        float scale=Math.min(.5f,Math.max(.15f,(width-129f)/210f));
        int w=Math.round(210*scale),h=Math.round(90*scale);
        return new Layout(Math.max(4,width-125-w),Math.max(4,height-24-h),w,h,scale);
    }
    private static int remaining(int value,long elapsed,int maximum){
        return (int)Math.max(0,Math.min(maximum,value)-Math.min(Integer.MAX_VALUE,Math.max(0,elapsed)));
    }
    /** The unified 18-second active bar includes charging. A separate six-second
     * cooldown begins at the active deadline, including between status packets. */
    public static Remaining remaining(int charge,int active,int cooldown,int invulnerable,long elapsed){
        int snapshotActive=Math.max(0,Math.min(DarkLiberationRules.DURATION,active));
        long passed=Math.max(0,elapsed);
        int cooldownLeft=snapshotActive>0?
            (passed<snapshotActive?0:remaining(DarkLiberationRules.COOLDOWN,passed-snapshotActive,DarkLiberationRules.COOLDOWN)):
            remaining(cooldown,passed,DarkLiberationRules.COOLDOWN);
        return new Remaining(remaining(charge,elapsed,DarkLiberationRules.CHARGE),
            remaining(snapshotActive,passed,DarkLiberationRules.DURATION),cooldownLeft,remaining(invulnerable,passed,200));
    }
    public static float progress(Remaining state){
        if(state.active()>0)return state.active()/(float)DarkLiberationRules.DURATION;
        return 1-state.cooldown()/(float)DarkLiberationRules.COOLDOWN;
    }
    public static String texture(Remaining state){
        return state.active()>0?"skill_active":state.cooldown()>0?"skill_cooldown":"skill_ready";
    }
    static int shieldPixels(float shield,float capacity,int width){
        if(!(shield>0)||!(capacity>0)||width<=0)return 0;
        return Math.max(1,(int)Math.round(width*Math.min(1,(double)shield/capacity)));
    }
    static String shieldLabel(float shield){
        var value=new BigDecimal(Float.toString(shield));
        var rounded=value.setScale(2,RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.signum()==0?value.stripTrailingZeros().toString():rounded.toPlainString();
    }
    public static int seconds(int ticks){return Math.max(0,(ticks+19)/20);}
}
