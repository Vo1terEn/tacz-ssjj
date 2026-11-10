package cn.judgfire;
import net.minecraftforge.common.ForgeConfigSpec;import cn.fusionarsenal.config.ArsenalConfig;
/** Compatibility facade. Never registers a second file. */
public final class EffectPerformanceConfig {
 public static final ForgeConfigSpec SPEC=ArsenalConfig.SPEC;
 public static final ForgeConfigSpec.BooleanValue REUSE=(ForgeConfigSpec.BooleanValue)ArsenalConfig.option("render.reuse_geometry"),NATIVE=(ForgeConfigSpec.BooleanValue)ArsenalConfig.option("render.fire_native");
 public static final ForgeConfigSpec.IntValue ENTRIES=(ForgeConfigSpec.IntValue)ArsenalConfig.option("render.surface_cache_entries"),VERTICES=(ForgeConfigSpec.IntValue)ArsenalConfig.option("render.max_cached_vertices");
 private EffectPerformanceConfig(){}public static void register(){}
 public static boolean reuse(){return ArsenalConfig.bool("render.reuse_geometry");}
 public static boolean nativeGeometry(){return ArsenalConfig.bool("render.fire_native");}
 public static int entries(){return ArsenalConfig.integer("render.surface_cache_entries");}
 public static int vertices(){return ArsenalConfig.integer("render.max_cached_vertices");}
}
