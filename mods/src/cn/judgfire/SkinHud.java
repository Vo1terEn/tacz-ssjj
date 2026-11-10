package cn.judgfire;

import net.minecraft.resources.ResourceLocation;

/** Cosmetic palette selection only; charge/cooldown and layout remain independent. */
public final class SkinHud {
  private static final ResourceLocation FILL = fx("t178_power_progress"),
      BG = fx("t179_power_progress_bg"),
      LIGHTNING_FILL = fx("l_power_progress"),
      LIGHTNING_BG = fx("l_power_progress_bg");
  private static final ResourceLocation READY = hud("burst_t038_r"),
      COOLDOWN = hud("burst_t239_r"),
      LIGHTNING_READY = hud("burst_lightning_ready_r");

  private static ResourceLocation fx(String name) {
    return new ResourceLocation("judgfire", "textures/fx/" + name + ".png");
  }

  private static ResourceLocation hud(String name) {
    return new ResourceLocation("judgfire", "textures/hud/" + name + ".png");
  }

  public static ResourceLocation crownFill(int skin) {
    return skin == WeaponSkins.LIGHTNING_SKIN ? LIGHTNING_FILL : FILL;
  }

  public static ResourceLocation crownBackground(int skin) {
    return skin == WeaponSkins.LIGHTNING_SKIN ? LIGHTNING_BG : BG;
  }

  public static ResourceLocation burstIcon(int skin, boolean ready) {
    return ready ? (skin == WeaponSkins.LIGHTNING_SKIN ? LIGHTNING_READY : READY) : COOLDOWN;
  }

  public static int readyColour(int skin) {
    return skin == WeaponSkins.LIGHTNING_SKIN ? 0xFFD6B3FF : 0xFF08E3FF;
  }
}
