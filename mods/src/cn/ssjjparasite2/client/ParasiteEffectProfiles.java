package cn.ssjjparasite2.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Set;

/** Common-side visual identity, shared by held models and spawned projectiles. */
public final class ParasiteEffectProfiles {
 private ParasiteEffectProfiles(){}
 public static final Set<String> KEYS=Set.of("parasitic_sniper","parasitic_sniper1","parasitic_sniper2","parasitic_sniper_frost","parasitic_sniper1_frost","parasitic_sniper2_frost","parasitic_sniper_ship","parasitic_sniper1_ship","parasitic_sniper2_ship");
 public static String base(ResourceLocation id){return ParasiteScopes.family(id)?id.getPath():null;}
 public static String key(ItemStack stack){
  if(stack==null||stack.isEmpty())return null;IGun gun=IGun.getIGunOrNull(stack);if(gun==null)return null;
  String base=base(gun.getGunId(stack));if(base==null)return null;
  ResourceLocation display=gun.getGunDisplayId(stack);
  for(String skin:new String[]{"frost","ship"})if(new ResourceLocation("ssjjparasite2",base+"_"+skin+"_display").equals(display))return base+"_"+skin;
  String skin=ParasiteScopes.skin(gun.getAttachmentId(stack,AttachmentType.SCOPE));return skin.isEmpty()?base:base+"_"+skin;
 }
 public static boolean hasImpact(String key){return key!=null&&(key.contains("_frost")||key.equals("parasitic_sniper2")||key.equals("parasitic_sniper2_ship"));}
}
