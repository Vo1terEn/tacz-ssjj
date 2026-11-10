package cn.ssjjdimension;

import net.minecraft.world.phys.Vec3;

/** Tick-based combat rules; no resource or client dependency. */
public final class DimensionDecoyRules {
 public static final int COOLDOWN=80,DASH=5,PROTECTION=20,DECOY=60,RAMP=100,RECOVERY=60;
 public static final double SPEED=1.2,MIN_DAMAGE=.001;
 private DimensionDecoyRules(){}
 public static int cooldownLimit(){return DimensionConfig.integer("dash_cooldown_ticks");}
 public static int dashDuration(){return DimensionConfig.integer("dash_ticks");}
 public static int protectionDuration(){return DimensionConfig.integer("protection_ticks");}
 public static int decoyDuration(){return DimensionConfig.integer("decoy_ticks");}
 public static int rampDuration(){return DimensionConfig.integer("overload_ramp_ticks");}
 public static int recoveryDuration(){return DimensionConfig.integer("overload_recovery_ticks");}
 public static double speed(){return DimensionConfig.number("dash_speed");}
 public static double minimumDamage(){return DimensionConfig.number("overload_min_damage");}
 public static double factor(double initial,long elapsed,boolean recovering){
  double t=Math.max(0,Math.min(1,elapsed/(double)(recovering?recoveryDuration():rampDuration())));
  return recovering?initial+(1-initial)*t:Math.max(minimumDamage(),initial*Math.pow(minimumDamage()/initial,t));
 }
 public static float protectedDamage(float damage){return damage*(float)(1-DimensionConfig.number("damage_reduction"));}
 public static boolean canDash(boolean active,int tier,long now,long ready){return active&&tier==2&&now>=ready;}
 /** Server yaw defines the local horizontal basis; client sends only discrete key axes. */
 public static Vec3 direction(float yaw,int forward,int left){
  if(!Float.isFinite(yaw)||forward< -1||forward>1||left< -1||left>1)return Vec3.ZERO;
  if(forward==0&&left==0)forward=1;
  double angle=Math.toRadians(yaw),sin=Math.sin(angle),cos=Math.cos(angle);
  return new Vec3(-sin*forward+cos*left,0,cos*forward+sin*left).normalize();
 }
}
