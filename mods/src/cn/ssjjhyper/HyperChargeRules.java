package cn.ssjjhyper;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Fixed gameplay contract, independent of packet timing and rendering. */
public final class HyperChargeRules {
    public static final int ENERGY=1000, COST=100, ROOT_COST=50, COOLDOWN=200, RECOVERY_PER_TICK=5;
    public static final int SLOW_TICKS=100, ROOT_TICKS=20, SHOT_WAIT=6, RING_TICKS=20, ENTER_TICKS=15;
    private HyperChargeRules(){}
    public static int duration(boolean grip){return HyperConfig.integer(grip?"grip_charge_duration_ticks":"charge_duration_ticks");}
    public static int secondPulse(boolean grip){return Math.min(duration(grip)-1,HyperConfig.integer(grip?"grip_second_pulse_ticks":"charge_second_pulse_ticks"));}
    public static int cap(int tier,boolean grip){return (int)Math.min(40,HyperConfig.integer(tier==2?"charge_tier2_mark_cap":"charge_tier1_mark_cap")*(grip?HyperConfig.number("grip_mark_multiplier"):1));}
    public static float radius(int tier,boolean grip){return (float)Math.min(128,HyperConfig.number(tier==2?"charge_tier2_radius":"charge_tier1_radius")*(grip?HyperConfig.number("grip_radius_multiplier"):1));}
    public static float damage(int priorSuccessfulHits){return damage(priorSuccessfulHits,false);}
    /** Increments are extra damage relative to the first 40-point detonation. */
    public static float damage(int priorSuccessfulHits,boolean ultimate){double step=HyperConfig.number(ultimate?"ultimate_increment":"charge_increment"),cap=HyperConfig.number(ultimate?"ultimate_increment_cap":"charge_increment_cap");return (float)(HyperConfig.number("charge_base_damage")*(1+Math.min(cap,step*Math.max(0,priorSuccessfulHits))));}
    public static int cost(){return HyperConfig.integer("charge_cost");}
    public static int rootCost(){return HyperConfig.integer("charge_root_cost");}
    public static int recoveryPerTick(){return HyperConfig.integer("charge_recovery_per_tick");}
    public static int slowTicks(){return HyperConfig.integer("charge_slow_ticks");}
    public static int rootTicks(){return HyperConfig.integer("charge_root_ticks");}
    public static int energyAfter(int energy,int cost,int kills){
        return (int)Math.max(0,Math.min(ENERGY,(long)energy-cost+(long)Math.max(0,kills)*cost));
    }
    /** Empty-to-full takes ten seconds; retained charge shortens the recovery proportionally. */
    public static int recoveryTicks(int energy){int deficit=ENERGY-Math.max(0,Math.min(ENERGY,energy));return (deficit+recoveryPerTick()-1)/recoveryPerTick();}
    public static int recoverEnergy(int start,long elapsed){
        int from=Math.max(0,Math.min(ENERGY,start));long ticks=Math.max(0,Math.min(COOLDOWN,elapsed));
        return (int)Math.min(ENERGY,from+ticks*recoveryPerTick());
    }
    public static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<=30000000&&Math.abs(v.y)<=30000000&&Math.abs(v.z)<=30000000;}
    /** An expanding cylinder sweeps the bounding box in XZ; target height is irrelevant. */
    public static boolean shell(Vec3 center,double inner,double outer,AABB b){
        if(center==null||b==null||outer<inner||inner<0)return false;
        double nx=Math.max(b.minX,Math.min(center.x,b.maxX))-center.x;
        double nz=Math.max(b.minZ,Math.min(center.z,b.maxZ))-center.z;
        double fx=Math.max(Math.abs(b.minX-center.x),Math.abs(b.maxX-center.x));
        double fz=Math.max(Math.abs(b.minZ-center.z),Math.abs(b.maxZ-center.z));
        return nx*nx+nz*nz<=outer*outer+1e-7&&fx*fx+fz*fz+1e-7>=inner*inner;
    }
    public static boolean viewport(Vec3 eye,Vec3 forward,Vec3 point,float verticalFov,float aspect){
        if(!finite(eye)||!finite(point)||forward==null||forward.lengthSqr()<1e-9||!validView(verticalFov,aspect))return false;
        Vec3 f=forward.normalize(),r=f.cross(new Vec3(0,1,0));
        if(r.lengthSqr()<1e-9)r=new Vec3(1,0,0);else r=r.normalize();
        Vec3 up=r.cross(f).normalize(),d=point.subtract(eye);double z=d.dot(f);
        if(z<=0)return false;
        double tangent=Math.tan(Math.toRadians(verticalFov*.5));
        return Math.abs(d.dot(up))<=z*tangent+1e-7&&Math.abs(d.dot(r))<=z*tangent*aspect+1e-7;
    }
    public static boolean validView(float fov,float aspect){return Float.isFinite(fov)&&fov>=10&&fov<=170&&Float.isFinite(aspect)&&aspect>=.25&&aspect<=6;}
}
