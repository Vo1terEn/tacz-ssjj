package cn.ssjjares;

import cn.fusionarsenal.config.ArsenalConfig;
import java.util.List;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;

/** Defines the six accessory profiles in the shared, server-synchronized configuration. */
public final class AresAccessoryConfig {
    public static final int MASK = 1;
    public static final int SHOULDER = 2;
    public static final int WINGS = 3;
    private static final List<String> PARTS = List.of("conquest_will", "extinction_resolve", "ruin_wings");

    private AresAccessoryConfig() {}

    public enum Stat {
        DAMAGE("damage_bonus_percent"),
        ARMOR("armor"),
        TOUGHNESS("armor_toughness"),
        RESISTANCE("energy_resistance"),
        PENETRATION("energy_penetration"),
        SPEED("movement_speed_percent"),
        REDUCTION("damage_reduction_percent"),
        FLIGHT("elytra_flight"),
        RAGE_COOLDOWN("rage_cooldown_seconds"),
        MISSILE_INTERVAL("missile_interval_seconds"),
        MISSILE_BASE("missile_base_damage"),
        MISSILE_HEALTH("missile_max_health_percent");

        private final String[][] paths = new String[PARTS.size()][2];

        Stat(String name) {
            for (int index = 0; index < PARTS.size(); index++) {
                String path = "ares.accessories." + PARTS.get(index) + ".";
                paths[index][0] = path + "normal." + name;
                paths[index][1] = path + "absolute." + name;
            }
        }

        private String key(int kind, int tier) { return paths[kind - 1][tier - 1]; }
        public double number(int kind, int tier) { return ArsenalConfig.number(key(kind, tier)); }
        public int integer(int kind, int tier) { return ArsenalConfig.integer(key(kind, tier)); }
        public boolean enabled(int kind, int tier) { return ArsenalConfig.bool(key(kind, tier)); }
    }

    public static Map<String, ForgeConfigSpec.ConfigValue<?>> define(
            Map<String, ForgeConfigSpec.ConfigValue<?>> values, ForgeConfigSpec.Builder builder,
            Map<String, Object> defaults) {
        for (int kind = MASK; kind <= WINGS; kind++) {
            for (int tier = 1; tier <= 2; tier++) {
                int primary = tier == 1 ? 1000 : 2350;
                integer(values, builder, defaults, Stat.DAMAGE.key(kind, tier), tier * 20, 0, 10000,
                    "盔甲栏和功能饰品栏每件提供的伤害加成百分数，20 代表 +20%。");
                integer(values, builder, defaults, Stat.ARMOR.key(kind, tier), kind == MASK ? 7 : 8, 0, 30,
                    "放在对应原版盔甲栏时的护甲值。");
                decimal(values, builder, defaults, Stat.TOUGHNESS.key(kind, tier), 5, 0, 20,
                    "放在对应原版盔甲栏时的护甲韧性。");
                switch (kind) {
                    case MASK -> {
                        decimal(values, builder, defaults, Stat.PENETRATION.key(kind, tier), primary, 0, 1.0E12,
                            "征战意志提供的能量抗性穿透。");
                        integer(values, builder, defaults, Stat.RAGE_COOLDOWN.key(kind, tier), 20, 0, 3600,
                            "装备征战意志时，战争狂怒结束后的冷却秒数。");
                    }
                    case SHOULDER -> {
                        decimal(values, builder, defaults, Stat.RESISTANCE.key(kind, tier), primary, 0, 1.0E12,
                            "灭世决意提供的能量抗性。");
                        integer(values, builder, defaults, Stat.MISSILE_INTERVAL.key(kind, tier), 10, 1, 3600,
                            "肩炮两轮追踪导弹之间的间隔秒数。");
                        decimal(values, builder, defaults, Stat.MISSILE_BASE.key(kind, tier), 10, 0, 1000000,
                            "每发追踪导弹的固定伤害部分。");
                        decimal(values, builder, defaults, Stat.MISSILE_HEALTH.key(kind, tier), 5, 0, 100,
                            "每发追踪导弹按目标最大生命值结算的百分数，5 代表 5%。");
                    }
                    case WINGS -> {
                        decimal(values, builder, defaults, Stat.SPEED.key(kind, tier), 20, 0, 1000,
                            "每件破败之翼的移速加成百分数，20 代表 +20%。");
                        decimal(values, builder, defaults, Stat.REDUCTION.key(kind, tier), 20, 0, 100,
                            "每件破败之翼的普通伤害减伤百分数，20 代表降低 20%。");
                        String flight = Stat.FLIGHT.key(kind, tier);
                        values.put(flight, builder.comment("破败之翼是否提供鞘翅飞行。").define(flight, true));
                        defaults.put(flight, true);
                        decimal(values, builder, defaults, Stat.RESISTANCE.key(kind, tier), tier == 1 ? 0 : 350, 0, 1.0E12,
                            "破败之翼提供的能量抗性。");
                        decimal(values, builder, defaults, Stat.PENETRATION.key(kind, tier), tier == 1 ? 0 : 350, 0, 1.0E12,
                            "破败之翼提供的能量抗性穿透。");
                    }
                }
            }
        }
        return values;
    }

    private static void integer(Map<String, ForgeConfigSpec.ConfigValue<?>> values, ForgeConfigSpec.Builder builder,
            Map<String, Object> defaults, String key, int value, int min, int max, String comment) {
        values.put(key, builder.comment(comment).defineInRange(key, value, min, max));
        defaults.put(key, value);
    }

    private static void decimal(Map<String, ForgeConfigSpec.ConfigValue<?>> values, ForgeConfigSpec.Builder builder,
            Map<String, Object> defaults, String key, double value, double min, double max, String comment) {
        values.put(key, builder.comment(comment).defineInRange(key, value, min, max));
        defaults.put(key, value);
    }

    public static int rageCooldownTicks(int tier) {
        return (tier == 0 ? 40 : Stat.RAGE_COOLDOWN.integer(MASK, tier)) * 20;
    }

    public static int missileIntervalTicks(int tier) {
        return Stat.MISSILE_INTERVAL.integer(SHOULDER, tier) * 20;
    }
}
