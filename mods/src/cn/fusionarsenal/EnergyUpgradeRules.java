package cn.fusionarsenal;

import net.minecraft.resources.ResourceLocation;

/** Compatibility facade. New weapon/attachment energy pairs belong in the build-time catalog. */
public final class EnergyUpgradeRules {
  public static final ResourceLocation EYE_GUN =
      new ResourceLocation("eyeofpunishment", "notdistinguish");
  public static final ResourceLocation UPGRADE_STONE =
      new ResourceLocation("eyeofpunishment", "ultimate_energy_stone");
  public static final double RESISTANCE = 800;
  public static final double PENETRATION = 1300;

  private EnergyUpgradeRules() {}

  public static double amount(
      ResourceLocation gunId, ResourceLocation laserId, boolean penetration) {
    return UpgradeCatalog.builtin().amount(gunId, laserId, penetration);
  }
}
