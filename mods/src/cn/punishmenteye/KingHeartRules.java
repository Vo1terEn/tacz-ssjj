package cn.punishmenteye;
import cn.fusionarsenal.config.ProgressionConfig;
/** Generated from confirmed project config. Shared outgoing bonus and pre-hit lightning threshold. */
public final class KingHeartRules {
 public static final String STOCK_ID="eyeofpunishment:king_heart";
 public static final float HEALTH_THRESHOLD=0.5f, MULTIPLIER=2.0f, ALL_DAMAGE_MULTIPLIER=1.1f;
 public static boolean canActivate(boolean alive,boolean spectator,boolean installed,long now,long ready){
  return alive&&!spectator&&installed&&now>=ready;
 }
 public static float lightning(float base,boolean active,float health,float maxHealth){
  return active&&maxHealth>0&&health>maxHealth*ProgressionConfig.number("eye.king_health_threshold")?(float)(base*ProgressionConfig.number("eye.king_lightning_multiplier")):base;
 }
 public static float allDamage(float base,boolean active){return active?(float)(base*ProgressionConfig.number("eye.king_damage_multiplier")):base;}
}
