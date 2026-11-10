package cn.judgfire;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One distance bonus shared by this weapon's dedicated laser-slot attachments. */
public final class CrownRangeBonus {
  private CrownRangeBonus() {}

  static boolean supports(ResourceLocation id) {
    return LaserAttachments.ULTIMATE.equals(id)
        || LaserAttachments.ENERGY.equals(id)
        || LaserAttachments.DIRECT.equals(id)
        || LaserAttachments.FUSION.equals(id);
  }

  static double multiplier(double distance) {
    if (Double.isNaN(distance)) distance = 0;
    return 1.10 + .40 * Math.max(0, Math.min(24, distance)) / 24;
  }

  static float apply(ResourceLocation id, double distance, float damage) {
    if (!supports(id)) return damage;
    return AttackBonuses.finite((double) damage * multiplier(distance));
  }

  static float apply(ItemStack stack, double distance, float damage) {
    var gun = IGun.getIGunOrNull(stack);
    if (gun == null || !JudgmentFlame.GUN.equals(gun.getGunId(stack))) return damage;
    return apply(gun.getAttachmentId(stack, AttachmentType.LASER), distance, damage);
  }
}
