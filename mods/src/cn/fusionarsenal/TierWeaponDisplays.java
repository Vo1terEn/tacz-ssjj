package cn.fusionarsenal;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Select each low tier after the original second-tier cosmetic synchronization. */
public final class TierWeaponDisplays {
  private static final ResourceLocation PIXEL = new ResourceLocation("judgfire:flame_crown_scope");
  private static final String PIXEL_DISPLAY = "judgfire:judgment_flame_display";
  private TierWeaponDisplays() {}

  public static void syncPixelDisplay(ItemStack stack) {
    var gun = IGun.getIGunOrNull(stack);
    if (gun == null) return;
    var gunId = gun.getGunId(stack);
    if (gunId == null) return;
    String id = gunId.toString();
    if (!id.equals("judgfire:judgment_flame_base") && !id.equals("judgfire:judgment_flame_tier1")) return;
    var tag = stack.getOrCreateTag();
    if (PIXEL.equals(gun.getAttachmentId(stack, AttachmentType.SCOPE))) tag.putString("GunDisplayId", PIXEL_DISPLAY);
    else {
      String scope = String.valueOf(gun.getAttachmentId(stack, AttachmentType.SCOPE));
      if (!scope.equals("judgfire:qinglong_scope") && !scope.equals("judgfire:frozen_scope") && !scope.equals("judgfire:lightning_scope"))
        // Old items can retain the second-tier base-video display in their NBT.
        // Reassert the exact gameplay tier after its scope synchronization.
        tag.putString("GunDisplayId", id + "_display");
    }
  }
}
