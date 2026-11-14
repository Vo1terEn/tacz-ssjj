package cn.ssjjzeus;

/** Skill timing uses online server ticks, never client clocks or packet values. */
public final class ZeusAdventRules {
    public static final int LIFETIME=400, STOCK_LIFETIME=500, RECHARGE=800, FIRE_INTERVAL=20, REQUEST_INTERVAL=4, CAST_INTERVAL=37;
    public static final double RANGE=64;
    public static final double FOCUS_RANGE=64;
    public record State(int charges,int progress,int capacity) {}
    private ZeusAdventRules() {}
    public static int recharge(){return ZeusConfig.integer("summon_recharge_ticks");}
    public static int fireInterval(int nativeRpm){return Math.max(1,(int)Math.ceil(1200d/Math.max(1,nativeRpm)));}
    public static int castInterval(){return Math.max(CAST_INTERVAL,ZeusConfig.integer("summon_interval_ticks"));}
    public static double range(){return ZeusConfig.number("phantom_range");}
    public static double focusRange(){return ZeusConfig.number("focus_range");}
    public static int capacity(boolean stock){return ZeusConfig.integer(stock?"stock_summon_capacity":"summon_capacity");}
    public static int limit(boolean stock){return ZeusConfig.integer(stock?"stock_phantom_limit":"phantom_limit");}
    public static int lifetime(boolean stock){return ZeusConfig.integer(stock?"stock_phantom_lifetime_ticks":"phantom_lifetime_ticks");}
    public static State full(int capacity){return new State(capacity,0,capacity);}
    public static State normalize(State state,int capacity){
        capacity=Math.max(1,Math.min(32,capacity));
        int charges=Math.max(0,Math.min(capacity,state.charges));
        return new State(charges,charges==capacity?0:Math.max(0,Math.min(recharge()-1,state.progress)),capacity);
    }
    public static State advance(State state,int capacity,int ticks){
        State s=normalize(state,capacity);if(s.charges==capacity)return s;
        long elapsed=(long)s.progress+Math.max(0,ticks);
        int charges=(int)Math.min(capacity,(long)s.charges+elapsed/recharge());
        return new State(charges,charges==capacity?0:(int)(elapsed%recharge()),capacity);
    }
    public static State spend(State state){
        State s=normalize(state,state.capacity);return s.charges==0?s:new State(s.charges-1,s.progress,s.capacity);
    }
    public static int remaining(State state){return state.charges>=state.capacity?0:recharge()-state.progress;}
    public static boolean canCast(State state,int active,int limit,long now,long last){
        return state.charges>0&&active>=0&&limit>0&&(last<0||now>=last&&now-last>=castInterval());
    }
}
