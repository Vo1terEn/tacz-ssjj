package cn.punishmenteye;
import cn.fusionarsenal.config.ProgressionConfig;
/** Tick deadlines: cooldown begins when the active window ends. */
public final class TimerRules {
 public static final int BASE_DURATION=400,STOCK_DURATION=600,BASE_COOLDOWN=400,STOCK_COOLDOWN=200;
 /** Wire validation maxima, not a particular equipped profile. */
 public static final int DURATION=72000,COOLDOWN=72000;
 public static int duration(boolean stock){return ProgressionConfig.eyeDuration(stock);}
 public static int cooldown(boolean stock){return ProgressionConfig.eyeCooldown(stock);}
 public static boolean profile(int duration,int cooldown){return validDuration(duration)&&cooldown>=1&&cooldown<=COOLDOWN;}
 public static boolean validDuration(int duration){return duration>=1&&duration<=DURATION;}
 public static long readyAt(long now){return readyAt(now,BASE_DURATION,BASE_COOLDOWN);}
 public static long readyAt(long now,int duration,int cooldown){return now+duration+cooldown;}
 public static long endEarly(long now,long activeUntil,long ready){return endEarly(now,activeUntil,ready,BASE_COOLDOWN);}
 public static long endEarly(long now,long activeUntil,long ready,int cooldown){return activeUntil>now?now+cooldown:ready;}
 public static long normalizeReady(long now,long activeUntil,long ready){return normalizeReady(now,activeUntil,ready,BASE_COOLDOWN);}
 public static long normalizeReady(long now,long activeUntil,long ready,int cooldown){return activeUntil>now?activeUntil+cooldown:Math.min(ready,now+cooldown);}
 public static long seconds(long ticks){return Math.max(0,(ticks+19)/20);}
}
