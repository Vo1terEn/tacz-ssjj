package ssjj.dark;

import net.minecraftforge.common.ForgeConfigSpec;

/** MC近战适配参数；原文没有公开的数值集中保留为可调整配置。 */
public final class DarkSettings {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SKILLS;
    public static final ForgeConfigSpec.BooleanValue PVP;
    public static final ForgeConfigSpec.DoubleValue COST;
    public static final ForgeConfigSpec.IntValue CHARGE;
    public static final ForgeConfigSpec.IntValue DURATION;
    public static final ForgeConfigSpec.IntValue COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue WAVE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue WAVE_RANGE;
    public static final ForgeConfigSpec.DoubleValue BLAST_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue BLAST_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SHIELD;
    public static final ForgeConfigSpec.DoubleValue RAGE;
    public static final ForgeConfigSpec.DoubleValue REDUCTION;
    public static final ForgeConfigSpec.DoubleValue SPEED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("黯水晶MC-PVE适配。原版模式规则未直接移植；下列数值除冷却参考外均为MC适配值。");
        SKILLS = b.define("skillsEnabled", true);
        PVP = b.comment("同时控制基础近战与技能对玩家的伤害；开启后仍遵守队伍与服务器PvP规则。").define("allowPlayerDamage", false);
        COST = b.defineInRange("currentHealthCostRatio", 0.20, 0.0, 0.9);
        CHARGE = b.defineInRange("chargeTicks", 60, 1, 200);
        DURATION = b.defineInRange("activeTicks", 200, 1, 2400);
        COOLDOWN = b.comment("参考源CdTimePara=10000毫秒；从强化结束后开始计时。").defineInRange("cooldownTicks", 200, 1, 24000);
        WAVE_DAMAGE = b.defineInRange("dragonWaveDamage", 3.0, 0.0, 200.0);
        WAVE_RANGE = b.defineInRange("dragonWaveRangeBlocks", 6.0, 1.0, 32.0);
        BLAST_DAMAGE = b.defineInRange("releaseDamage", 12.0, 0.0, 200.0);
        BLAST_RADIUS = b.defineInRange("releaseRadiusBlocks", 4.0, 1.0, 16.0);
        SHIELD = b.defineInRange("extraShieldMaxHealthRatio", 0.30, 0.0, 2.0);
        RAGE = b.defineInRange("maxMissingHealthDamageBonus", 0.50, 0.0, 3.0);
        REDUCTION = b.defineInRange("secondGradeMaxReduction", 0.20, 0.0, 0.8);
        SPEED = b.defineInRange("activeMovementSpeedBonus", 0.20, 0.0, 1.0);
        SPEC = b.build();
    }

    private DarkSettings() {}
}
