package cn.judgfire;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Cosmetic scope selection; gun identity and all gameplay attachments stay. */
public final class WeaponSkins {
  public static final int ORIGINAL_SKIN = 0, QINGLONG_SKIN = 1, FROZEN_SKIN = 2, LIGHTNING_SKIN = 3;
  public static final ResourceLocation BASE_VIDEO_DISPLAY =
      new ResourceLocation("judgfire", "judgment_flame_base_video_display");
  private static final ResourceLocation PIXEL_SCOPE =
      new ResourceLocation("judgfire", "flame_crown_scope");
  public static final ResourceLocation QINGLONG =
      new ResourceLocation("judgfire", "qinglong_scope");
  public static final ResourceLocation DISPLAY =
      new ResourceLocation("judgfire", "judgment_flame_qinglong_display");
  public static final ResourceLocation FROZEN = new ResourceLocation("judgfire", "frozen_scope"),
      FROZEN_DISPLAY = new ResourceLocation("judgfire", "judgment_flame_frozen_display");
  public static final ResourceLocation
      LIGHTNING = new ResourceLocation("judgfire", "lightning_scope"),
      LIGHTNING_DISPLAY = new ResourceLocation("judgfire", "judgment_flame_lightning_display");
  public static final ResourceLocation LEGACY_SCOPE =
      new ResourceLocation("judgfire", "integrated_scope");

  public static boolean qinglong(ItemStack stack) {
    return skin(stack) == QINGLONG_SKIN;
  }

  public static int skin(ItemStack stack) {
    var gun = IGun.getIGunOrNull(stack);
    if (gun == null || !JudgmentFlame.GUN.equals(gun.getGunId(stack))) return 0;
    return displaySkin(displayForScope(gun.getAttachmentId(stack, AttachmentType.SCOPE)));
  }

  public static int displaySkin(ResourceLocation display) {
    if (DISPLAY.equals(display)) return QINGLONG_SKIN;
    if (FROZEN_DISPLAY.equals(display)) return FROZEN_SKIN;
    if (LIGHTNING_DISPLAY.equals(display)) return LIGHTNING_SKIN;
    return ORIGINAL_SKIN;
  }

  private static ResourceLocation displayForScope(ResourceLocation scope) {
    if (QINGLONG.equals(scope)) return DISPLAY;
    if (FROZEN.equals(scope)) return FROZEN_DISPLAY;
    if (LIGHTNING.equals(scope)) return LIGHTNING_DISPLAY;
    return PIXEL_SCOPE.equals(scope) ? null : BASE_VIDEO_DISPLAY;
  }

  public static void sync(ItemStack stack) {
    var gun = IGun.getIGunOrNull(stack);
    if (gun == null || !JudgmentFlame.GUN.equals(gun.getGunId(stack))) return;
    // Remove only the old auto-created controller, without producing another item.
    // Real player-selected skin scopes and every other attachment remain intact.
    if (LEGACY_SCOPE.equals(gun.getAttachmentId(stack, AttachmentType.SCOPE)))
      gun.unloadAttachment(stack, AttachmentType.SCOPE);
    syncDisplay(stack.getOrCreateTag(), gun.getAttachmentId(stack, AttachmentType.SCOPE));
  }

  static void syncDisplay(CompoundTag tag, ResourceLocation scope) {
    String key = GunItemDataAccessor.GUN_DISPLAY_ID_TAG;
    ResourceLocation display = displayForScope(scope);
    if (display != null) tag.putString(key, display.toString());
    else if (ownsDisplay(tag.getString(key)))
      // TaCZ setGunDisplayId(stack,null) is a no-op. Remove the actual tag instead.
      tag.remove(key);
  }

  private static boolean ownsDisplay(String display) {
    return BASE_VIDEO_DISPLAY.toString().equals(display)
        || DISPLAY.toString().equals(display)
        || FROZEN_DISPLAY.toString().equals(display)
        || LIGHTNING_DISPLAY.toString().equals(display);
  }
}
