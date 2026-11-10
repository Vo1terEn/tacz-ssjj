package cn.fusionarsenal.api;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Extension point for each weapon's own ultimate-stone skill checks. */
public final class UltimateStoneRegistry {
 public static final ResourceLocation CREATIVE=new ResourceLocation("ssjjrpg:creative_upgrade_stone");
 private static final Map<ResourceLocation,ResourceLocation> STONES=new ConcurrentHashMap<>();
 static {
  register(new ResourceLocation("judgfire:judgment_flame"),new ResourceLocation("judgfire:ultimate_upgrade_stone"));
  register(new ResourceLocation("eyeofpunishment:notdistinguish"),new ResourceLocation("eyeofpunishment:ultimate_stone"));
 }
 private UltimateStoneRegistry(){}
 /** Register every eligible gun ID. Calling again with the same binding is harmless. */
 public static void register(ResourceLocation gun,ResourceLocation nativeStone){
  Objects.requireNonNull(gun,"gun");Objects.requireNonNull(nativeStone,"nativeStone");
  if(nativeStone.equals(CREATIVE))throw new IllegalArgumentException("Native ultimate stone must differ from the creative stone");
  ResourceLocation previous=STONES.putIfAbsent(gun,nativeStone);
  if(previous!=null&&!previous.equals(nativeStone))throw new IllegalArgumentException("Ultimate stone already registered for "+gun);
 }
 public static ResourceLocation nativeStone(ResourceLocation gun){return gun==null?null:STONES.get(gun);}
 public static Map<ResourceLocation,ResourceLocation> snapshot(){return Map.copyOf(STONES);}
 /** Native or creative installation must belong to this exact weapon's registered skill. */
 public static boolean matches(ResourceLocation gun,ResourceLocation installed,ResourceLocation nativeStone){
  ResourceLocation expected=nativeStone(gun);
  return expected!=null&&expected.equals(nativeStone)&&(expected.equals(installed)||CREATIVE.equals(installed));
 }
 public static boolean installed(ItemStack stack,ResourceLocation nativeStone){
  if(stack==null)return false;var gun=IGun.getIGunOrNull(stack);
  if(gun==null||!gun.allowAttachmentType(stack,AttachmentType.LASER))return false;
  var gunId=gun.getGunId(stack);var attachment=gun.getAttachmentId(stack,AttachmentType.LASER);
  return matches(gunId,attachment,nativeStone)&&AllowAttachmentTagMatcher.match(gunId,attachment);
 }
}
