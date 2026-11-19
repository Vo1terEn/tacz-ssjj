package cn.ssjjdark;

/** Original weapon.csv attack delays and inter-action intervals, in Minecraft ticks. */
public final class DarkDamageRules {
    public static final double REACH=3.0;
    private DarkDamageRules(){}
    public record Strike(float damage,int prepTicks,int cooldownTicks){}
    public static Strike strike(boolean heavy){return heavy?new Strike(20,10,20):new Strike(12,3,8);}
    public static int intervalTicks(boolean previousHeavy,boolean nextHeavy){return previousHeavy?20:nextHeavy?10:8;}
    public static boolean validSlot(int slot){return slot>=0&&slot<9;}
    public static boolean gun(String namespace,String path){
        return "ssjj_dark".equals(namespace)&&("darkdiamond".equals(path)||"darkdiamond1".equals(path)||"darkdiamond2".equals(path));
    }
    public static boolean withinReach(double distanceSquared){
        return Double.isFinite(distanceSquared)&&distanceSquared>=0&&distanceSquared<=REACH*REACH;
    }
    /** Player-owned cooldown survives weapon swaps; a pending hit can be consumed once only. */
    public static final class Sequence {
        private long lastStart,hitAt;
        private boolean started,lastHeavy;
        private Strike pending;
        public boolean begin(long now,boolean heavy){
            if(pending!=null||started&&now<nextAllowed(heavy))return false;
            pending=strike(heavy);hitAt=now+pending.prepTicks();lastStart=now;lastHeavy=heavy;started=true;return true;
        }
        public boolean due(long now){return pending!=null&&now>=hitAt;}
        public Strike consume(long now){if(!due(now))return null;var result=pending;pending=null;return result;}
        public void cancel(){pending=null;}
        public long nextAllowed(boolean nextHeavy){return started?lastStart+intervalTicks(lastHeavy,nextHeavy):Long.MIN_VALUE;}
    }
}
