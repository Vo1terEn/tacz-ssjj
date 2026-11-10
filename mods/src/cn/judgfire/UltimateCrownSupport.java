package cn.judgfire;
import cn.fusionarsenal.api.UltimateStoneRegistry;
import net.minecraft.world.item.ItemStack;
/** Use the shared legal installation check before applying the existing crown formula. */
public final class UltimateCrownSupport {
 private UltimateCrownSupport(){}
 public static float apply(ItemStack stack,double distance,float amount){
  return UltimateStoneRegistry.installed(stack,LaserAttachments.ULTIMATE)?CrownRangeBonus.apply(LaserAttachments.ULTIMATE,distance,amount):amount;
 }
}
