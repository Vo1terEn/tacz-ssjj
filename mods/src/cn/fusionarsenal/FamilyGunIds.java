package cn.fusionarsenal;

import net.minecraft.resources.ResourceLocation;

/** Narrow aliases for gameplay eligibility; stack identity and TaCZ display IDs remain distinct. */
public final class FamilyGunIds {
  public static final ResourceLocation FIRE = new ResourceLocation("judgfire:judgment_flame");
  public static final ResourceLocation EYE = new ResourceLocation("eyeofpunishment:notdistinguish");
  private FamilyGunIds() {}

  public static ResourceLocation canonical(ResourceLocation id) {
    if (id == null) return null;
    return switch(id.toString()) {
      case "judgfire:judgment_flame_base", "judgfire:judgment_flame_tier1" -> FIRE;
      case "eyeofpunishment:notdistinguish_base", "eyeofpunishment:notdistinguish_tier1" -> EYE;
      default -> id;
    };
  }

  public static boolean sameFamily(ResourceLocation receiver, Object other) {
    return canonical(receiver).equals(other instanceof ResourceLocation id ? canonical(id) : other);
  }
}
