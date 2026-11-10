package cn.fusionarsenal;

import cn.fusionarsenal.api.UltimateStoneRegistry;
import cn.fusionarsenal.config.ArsenalConfig;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Shared laser attachment stats; independent of weapon families and registered skill aliases. */
public final class CreativeStone {
 public static final ResourceLocation ID=UltimateStoneRegistry.CREATIVE;
 private CreativeStone(){}
 public static boolean installed(ItemStack stack){
  if(stack==null)return false;var gun=IGun.getIGunOrNull(stack);
  return gun!=null&&gun.allowAttachmentType(stack,AttachmentType.LASER)&&
    ID.equals(gun.getAttachmentId(stack,AttachmentType.LASER))&&AllowAttachmentTagMatcher.match(gun.getGunId(stack),ID);
 }
 public static double amount(ItemStack stack,boolean penetration){return installed(stack)?ArsenalConfig.number(penetration?"combat.creative_stone_penetration":"combat.creative_stone_resistance"):0;}
 public static double strength(ItemStack stack){return installed(stack)?ArsenalConfig.number("combat.creative_stone_strength"):0;}
 public static double amount(ResourceLocation gun,ResourceLocation attachment,boolean penetration){
  return gun!=null&&ID.equals(attachment)&&AllowAttachmentTagMatcher.match(gun,ID)?ArsenalConfig.number(penetration?"combat.creative_stone_penetration":"combat.creative_stone_resistance"):0;
 }
}
