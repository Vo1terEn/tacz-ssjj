package cn.punishmenteye;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Scope-slot cosmetics choose a native display, never a different gameplay gun. */
public final class SkinRules {
 public static final String[] KEYS={"timetravel","sakura","anime","fire"};
 public static String key(ResourceLocation gun,ResourceLocation scope){
  if(!WeaponIdentity.matches(gun)||scope==null)return "";
  for(String key:KEYS)if(scope.equals(new ResourceLocation("eyeofpunishment","skin_"+key)))return key;
  return "";
 }
 public static ResourceLocation display(ResourceLocation gun,ResourceLocation scope){
  String key=key(gun,scope);return key.isEmpty()?null:new ResourceLocation("eyeofpunishment","notdistinguish_"+key+"_display");
 }
 public static ResourceLocation displayOverride(ItemStack stack){
  if(!(stack.getItem() instanceof IGun gun)||!WeaponIdentity.matches(gun.getGunId(stack)))return null;
  return display(gun.getGunId(stack),gun.getAttachmentId(stack,AttachmentType.SCOPE));
 }
}
