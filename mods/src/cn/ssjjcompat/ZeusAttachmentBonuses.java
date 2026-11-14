package cn.ssjjcompat;

import cn.ssjjzeus.ZeusConfig;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Additional native energy attributes from actually installed Zeus-exclusive parts. */
public final class ZeusAttachmentBonuses {
 private static final Set<String> GUNS=Set.of("hand_of_zeuss","hand_of_zeuss1","hand_of_zeuss2");
 private static final Map<AttachmentType,ResourceLocation> PARTS=Map.of(
  AttachmentType.MUZZLE,new ResourceLocation("ssjj_zeus","thunder_burst"),
  AttachmentType.EXTENDED_MAG,new ResourceLocation("ssjj_zeus","thunder_energy_mag"),
  AttachmentType.STOCK,new ResourceLocation("ssjj_zeus","thunder_advent_stock"),
  AttachmentType.GRIP,new ResourceLocation("ssjj_zeus","thunder_shock_grip"));
 private ZeusAttachmentBonuses() {}
 public static int count(ItemStack stack) {
  if(stack==null||stack.isEmpty())return 0;
  var gun=IGun.getIGunOrNull(stack);if(gun==null)return 0;
  var gunId=gun.getGunId(stack);
  if(gunId==null||!"ssjj_zeus".equals(gunId.getNamespace())||!GUNS.contains(gunId.getPath()))return 0;
  int result=0;
  for(var entry:PARTS.entrySet()) {
   var slot=entry.getKey();if(!gun.allowAttachmentType(stack,slot))continue;
   var installed=gun.getAttachmentId(stack,slot);
   if(!entry.getValue().equals(installed))continue;
   if(!TimelessAPI.getCommonAttachmentIndex(installed).map(index->index.getType()==slot).orElse(false))continue;
   if(!AllowAttachmentTagMatcher.match(gunId,installed))continue;
   result++;
  }
  return result;
 }
 public static double energy(double original,ItemStack stack,boolean penetration) {
  return original+count(stack)*ZeusConfig.number(penetration?"attachment_energy_penetration":"attachment_energy_resistance");
 }
 public static double strength(double original,ItemStack stack) {
  return original+count(stack)*ZeusConfig.number("attachment_energy_strength");
 }
}
