package cn.punishmenteye;
import cn.fusionarsenal.config.ProgressionConfig;

/** Held-weapon skill policy; a stone never writes or extends the manual deadlines. */
public final class UltimateRules {
 public static final String STONE_ID="eyeofpunishment:ultimate_stone", ENERGY_STONE_ID="eyeofpunishment:ultimate_energy_stone";
 public static final float ALL_DAMAGE_MULTIPLIER=1.15f;
 public record Status(int active,int cooldown,boolean permanent){}
 public static boolean permanent(boolean alive,boolean spectator,boolean heldStock,boolean heldStone){
  return alive&&!spectator&&heldStock&&heldStone;
 }
 public static boolean active(boolean alive,boolean spectator,boolean heldStock,boolean heldStone,long now,long manualUntil){
  return alive&&!spectator&&heldStock&&(heldStone||manualUntil>now);
 }
 public static Status status(boolean active,boolean stone,long now,long manualUntil,long ready){
  return status(active,stone,now,manualUntil,ready,TimerRules.BASE_DURATION,TimerRules.BASE_COOLDOWN);
 }
 public static Status status(boolean active,boolean stone,long now,long manualUntil,long ready,int duration,int cooldown){
  if(active&&stone)return new Status(0,0,true);
  return new Status(active?(int)Math.max(0,Math.min(duration,manualUntil-now)):0,
   (int)Math.max(0,Math.min(cooldown,ready-now)),false);
 }
 public static float allDamage(float base,boolean active,boolean stone){
  return active?(float)(base*ProgressionConfig.number(stone?"eye.stone_damage_multiplier":"eye.king_damage_multiplier")):base;
 }
}
