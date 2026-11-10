package cn.punishmenteye;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Reads the actual native LASER attachment, never a loose inventory item. */
public final class UltimateRequirement {
 public static final ResourceLocation STONE=new ResourceLocation(UltimateRules.STONE_ID), ENERGY_STONE=new ResourceLocation(UltimateRules.ENERGY_STONE_ID);
 public static boolean matches(ResourceLocation gun,ResourceLocation stone){
  return GripRequirement.WEAPON.equals(gun)&&(STONE.equals(stone)||ENERGY_STONE.equals(stone));
 }
 public static boolean installed(ItemStack stack){
  return stack.getItem() instanceof IGun gun&&matches(gun.getGunId(stack),gun.getAttachmentId(stack,AttachmentType.LASER));
 }
}
