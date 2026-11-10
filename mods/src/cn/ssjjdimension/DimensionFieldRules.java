package cn.ssjjdimension;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server tick boundaries and geometry shared by gameplay and offline checks. */
public final class DimensionFieldRules {
    public static final int ACTIVE_TICKS=300,COOLDOWN_TICKS=100,FIELD_TICKS=60,CAST_INTERVAL=6,MAX_FIELDS=3,STUN_TICKS=10;
    public static final float DAMAGE=30,BONUS_DAMAGE=30,RADIUS=5f,ORIGINAL_RADIUS=3f;
    public static final double FIELD_BELOW=.5,FIELD_ABOVE=3;
    public static final double RANGE=64;
    public record State(long activeUntil,long cooldownUntil,long nextCast,int activationDebt) {
        public State(long activeUntil,long cooldownUntil,long nextCast){this(activeUntil,cooldownUntil,nextCast,0);}
    }
    private DimensionFieldRules() {}
    public static float radius(int tier) { return (float)DimensionConfig.number(tier==2?"field_radius_tier2":"field_radius_tier1"); }
    public static int activeDuration(){return DimensionConfig.integer("active_ticks");}
    public static int recharge(){return DimensionConfig.integer("recharge_ticks");}
    public static int fieldDuration(){return DimensionConfig.integer("field_ticks");}
    public static int castInterval(){return DimensionConfig.integer("cast_interval_ticks");}
    public static int maxFields(){return DimensionConfig.integer("max_fields");}
    public static int stunTicks(){return DimensionConfig.integer("field_stun_ticks");}
    public static float damage(){return (float)DimensionConfig.number("field_damage");}
    public static float bonusDamage(){return (float)DimensionConfig.number("field_bonus_damage");}
    public static double range(){return DimensionConfig.number("cast_range");}
    public static boolean actionReady(com.tacz.guns.api.entity.IGunOperator operator){
        if(!activationReady(operator))return false;
        var reload=operator.getSynReloadState();
        return reload==null||!reload.getStateType().isReloading();
    }
    public static boolean activationReady(com.tacz.guns.api.entity.IGunOperator operator){return operator!=null&&operator.getSynDrawCoolDown()<=0&&!operator.getSynIsBolting()&&operator.getSynMeleeCoolDown()<=0;}
    public static State idle() { return new State(0,0,0); }
    public static boolean canActivate(State s,long now) { return s!=null&&activeTicks(s,now)==0&&cooldownTicks(s,now)<recharge(); }
    public static State activate(State s,long now) { return canActivate(s,now)?new State(now+duration(s,now),now+duration(s,now)+recharge(),Math.max(now,s.nextCast()),cooldownTicks(s,now)):s; }
    public static int duration(State s,long now) { return Math.max(1,(int)((long)(recharge()-cooldownTicks(s,now))*activeDuration()/recharge())); }
    public static boolean canCast(State s,long now) { return s!=null&&activeTicks(s,now)>0&&now>=s.nextCast(); }
    public static State cast(State s,long now) { return canCast(s,now)?new State(s.activeUntil(),s.cooldownUntil(),now+castInterval(),s.activationDebt()):s; }
    public static State finish(State s,long now) { return s==null?idle():activeTicks(s,now)>0?new State(0,now+recharge(),0):new State(0,s.cooldownUntil(),0); }
    /** Manual shutdown preserves the unused energy and recharges only the spent part. */
    public static State deactivate(State s,long now) {
        if(s==null||activeTicks(s,now)==0)return s==null?idle():s;
        long spent=Math.max(0,activeDuration()-activeTicks(s,now));
        long debt=Math.max(s.activationDebt(),(spent*recharge()+activeDuration()-1)/activeDuration());
        return new State(0,now+debt,s.nextCast());
    }
    public static int activeTicks(State s,long now) { return s==null?0:remaining(s.activeUntil(),now); }
    public static int cooldownTicks(State s,long now) { return s==null||activeTicks(s,now)>0?0:remaining(s.cooldownUntil(),now); }
    private static int remaining(long until,long now) { return (int)Math.max(0,Math.min(Integer.MAX_VALUE,until-now)); }
    /** Bounding-box contact counts as inside, including large and multipart living cores. */
    public static boolean contains(Vec3 center,double radius,AABB box) {
        if(center==null||box==null||!Double.isFinite(radius)||radius<=0)return false;
        double x=Math.max(box.minX,Math.min(box.maxX,center.x));
        double y=Math.max(box.minY,Math.min(box.maxY,center.y));
        double z=Math.max(box.minZ,Math.min(box.maxZ,center.z));
        double dx=center.x-x,dy=center.y-y,dz=center.z-z;
        return dx*dx+dy*dy+dz*dz<=radius*radius;
    }
    /** Source field is a ground circle, extended upward so ordinary living bodies count. */
    public static boolean containsField(Vec3 center,double radius,AABB box) {
        if(center==null||box==null||!Double.isFinite(radius)||radius<=0
            ||box.maxY<center.y-FIELD_BELOW||box.minY>center.y+FIELD_ABOVE)return false;
        double x=Math.max(box.minX,Math.min(box.maxX,center.x));
        double z=Math.max(box.minZ,Math.min(box.maxZ,center.z));
        double dx=center.x-x,dz=center.z-z;
        return dx*dx+dz*dz<=radius*radius;
    }
    public static boolean finite(Vec3 p) { return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)
        &&Math.abs(p.x)<=30000000&&Math.abs(p.y)<=30000000&&Math.abs(p.z)<=30000000; }
}
