package cn.ssjjdimension;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Native skin attachment and matching display choose the original effect profile. */
public final class DimensionVisualScope {
 public static final List<String> SKINS=List.of("","frozen","neon","royal");
 private DimensionVisualScope() {}
 public static boolean isSkin(String skin){return skin!=null&&SKINS.contains(skin);}
 public static String profile(int tier,String skin){if(tier<0||tier>2||!isSkin(skin))throw new IllegalArgumentException("Unknown Dimension effect profile");return "tier"+tier+(skin.isEmpty()?"":"_"+skin);}
 public static int tier(ItemStack stack){var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);var id=gun==null?null:gun.getGunId(stack);if(id==null||!"ssjj_dimension".equals(id.getNamespace()))return -1;return switch(id.getPath()){case "dimension_star"->0;case "dimension_star1"->1;case "dimension_star2"->2;default->-1;};}
 public static boolean accepts(ItemStack stack){return selectedSkin(stack)!=null;}
 public static String skin(ItemStack stack){String selected=selectedSkin(stack);return selected==null?"":selected;}
 private static String selectedSkin(ItemStack stack) {
  if(stack==null||stack.isEmpty())return null;
  var gun=IGun.getIGunOrNull(stack);if(gun==null)return null;
  var id=gun.getGunId(stack);if(tier(stack)<0)return null;
  String attached="";var attachment=gun.getAttachmentId(stack,AttachmentType.SCOPE);for(String candidate:SKINS)if(!candidate.isEmpty()&&new ResourceLocation("ssjj_dimension",candidate+"_scope").equals(attachment)){attached=candidate;break;}
  var display=gun.getGunDisplayId(stack);
  if(DefaultAssets.DEFAULT_GUN_DISPLAY_ID.equals(display)||new ResourceLocation("ssjj_dimension",id.getPath()+"_display").equals(display))return attached;
  for(String candidate:SKINS)if(!candidate.isEmpty()&&new ResourceLocation("ssjj_dimension",id.getPath()+"_"+candidate+"_display").equals(display))return attached.isEmpty()?candidate:attached;
  return null;
 }
 public static boolean defaultTier2(ItemStack stack) {
  if(!accepts(stack)||!skin(stack).isEmpty())return false;
  var gun=IGun.getIGunOrNull(stack);return new ResourceLocation("ssjj_dimension","dimension_star2").equals(gun.getGunId(stack));
 }
}
