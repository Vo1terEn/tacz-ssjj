package cn.fusionarsenal.config;
import java.util.*;import net.minecraftforge.common.ForgeConfigSpec;
/** Compatibility facade; all values now use the single global ArsenalConfig. */
public final class ProgressionConfig {
 public static final String FILE=ArsenalConfig.FILE;public static final ForgeConfigSpec SPEC=ArsenalConfig.SPEC;
 private static final Set<String> KEYS=Set.of("fire.crown_enabled","fire.lotus_enabled","fire.burst_enabled","eye.passive_enabled","eye.king_enabled","fire.damage_base","fire.crown_range_base","eye.gaze_bonus_base","eye.lightning_scale_base","eye.ball_base","fire.damage_tier1","fire.crown_range_tier1","eye.gaze_bonus_tier1","eye.lightning_scale_tier1","eye.ball_tier1","fire.damage_tier2","fire.crown_range_tier2","eye.gaze_bonus_tier2","eye.lightning_scale_tier2","eye.ball_tier2","fire.stock_range_multiplier","fire.lotus_reduction_tier1","fire.lotus_reduction_tier2","fire.grip_reduction_multiplier","fire.burst_multiplier","fire.magazine_burst_multiplier","fire.accessory_strength","fire.accessory_resistance","fire.accessory_penetration","eye.grip_gaze_multiplier","eye.accessory_strength","eye.accessory_resistance","eye.accessory_penetration","eye.lightning_damage","eye.cloud_damage","eye.king_health_threshold","eye.king_lightning_multiplier","eye.king_energy_strength","eye.speed_level","eye.grip_speed_level","eye.king_duration_seconds","eye.king_cooldown_seconds","eye.stock_duration_seconds","eye.stock_cooldown_seconds","eye.ball_interval_shots","eye.cloud_pulse_ticks");
 private ProgressionConfig(){}
 public static void register(){ArsenalConfig.register();}
 public static Object value(String key){if(!KEYS.contains(key))throw new IllegalArgumentException(key);return ArsenalConfig.value(key);}
 public static double number(String key){return ((Number)value(key)).doubleValue();}
 public static int integer(String key){return ((Number)value(key)).intValue();}
 public static boolean enabled(String key){return (Boolean)value(key);}
 public static Map<String,Object> snapshot(){var m=new LinkedHashMap<String,Object>();for(String k:KEYS)m.put(k,value(k));return Collections.unmodifiableMap(m);}
 public static Map<String,Object> defaults(){var m=new LinkedHashMap<String,Object>();for(String k:KEYS)m.put(k,ArsenalConfig.defaults().get(k));return Collections.unmodifiableMap(m);}
 public static double fireDamage(int tier){return number("fire.damage_"+tierName(tier));}
 public static double fireRange(int tier){return number("fire.crown_range_"+tierName(tier));}
 public static double gazeBonus(int tier){return number("eye.gaze_bonus_"+tierName(tier));}
 public static double lightningScale(int tier){return number("eye.lightning_scale_"+tierName(tier));}
 public static boolean ballAllowed(int tier){return tier>=0&&tier<=2&&enabled("eye.ball_"+tierName(tier));}
 public static int eyeDuration(boolean stock){return integer(stock?"eye.stock_duration_seconds":"eye.king_duration_seconds")*20;}
 public static int eyeCooldown(boolean stock){return integer(stock?"eye.stock_cooldown_seconds":"eye.king_cooldown_seconds")*20;}
 public static boolean validWeaponState(int duration,int active,boolean permanent){return duration>=1&&duration<=72000&&active>=0&&active<=duration&&(!permanent||active==0);}
 private static String tierName(int tier){return switch(tier){case 0->"base";case 1->"tier1";case 2->"tier2";default->throw new IllegalArgumentException("Unknown tier: "+tier);};}
}
