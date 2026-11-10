package cn.punishmenteye;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A loose stock or a stock on another gun cannot unlock the skill. */
public final class StockRequirement {
 public static final ResourceLocation STOCK=new ResourceLocation(KingHeartRules.STOCK_ID);
 public static boolean matches(ResourceLocation gun,ResourceLocation stock){
  return GripRequirement.WEAPON.equals(gun)&&STOCK.equals(stock);
 }
 public static boolean installed(ItemStack stack){
  return stack.getItem() instanceof IGun gun&&matches(gun.getGunId(stack),gun.getAttachmentId(stack,AttachmentType.STOCK));
 }
}
