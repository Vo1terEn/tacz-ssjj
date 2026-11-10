package cn.judgfire;
import net.minecraft.resources.ResourceLocation;import net.minecraftforge.common.ForgeConfigSpec;import cn.fusionarsenal.config.ArsenalConfig;
/** Existing public combat API; all active values come from ssjjrpg.toml. */
public final class CombatConfig {
 public static final String FILE=ArsenalConfig.FILE;public static final ForgeConfigSpec SPEC=ArsenalConfig.SPEC;
 private static double n(String key){return ArsenalConfig.number("combat."+key);}
 public static float weaponDamage(){return (float)n("weapon_damage");}public static float crownDamage(){return (float)n("crown_damage");}
 public static float burstWeaponDamage(){return (float)(weaponDamage()*ArsenalConfig.number("fire.magazine_burst_multiplier"));}
 public static float burstCrownDamage(){return (float)(crownDamage()*ArsenalConfig.number("fire.magazine_burst_multiplier"));}
 public static double reduction(){return ArsenalConfig.number("fire.lotus_reduction_tier2");}
 public static double range(){return ArsenalConfig.number("fire.crown_range_tier2");}
 public static boolean weaponThroughWalls(){return ArsenalConfig.bool("combat.weapon_through_walls");}public static boolean crownThroughWalls(){return ArsenalConfig.bool("combat.crown_through_walls");}
 public static double burstEnergy(boolean penetration){return n(penetration?"burst_penetration":"burst_resistance");}
 public static double attachmentEnergy(ResourceLocation id,boolean penetration){String key=LaserAttachments.ENERGY.equals(id)?"guard":LaserAttachments.DIRECT.equals(id)?"direct":LaserAttachments.FUSION.equals(id)?"fusion":null;return key==null?0:n(key+(penetration?"_penetration":"_resistance"));}
 public static boolean inRange(double x,double y,double z){double r=range();return r>0&&Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z)))<=r;}
 private CombatConfig(){}
}
