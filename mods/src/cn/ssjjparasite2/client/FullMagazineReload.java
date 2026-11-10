package cn.ssjjparasite2.client;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Creative/no-ammo-check paths also obey the current magazine's capacity. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class FullMagazineReload {
 @SubscribeEvent(priority=EventPriority.HIGHEST)
 public static void onReload(GunReloadEvent event){
  var stack=event.getGunItemStack();var gun=IGun.getIGunOrNull(stack);
  if(gun==null)return;var id=gun.getGunId(stack);if(!ParasiteScopes.family(id))return;
  TimelessAPI.getCommonGunIndex(id).ifPresent(index->apply(event,id,gun.getCurrentAmmoCount(stack),AttachmentDataUtils.getAmmoCountWithAttachment(stack,index.getGunData())));
 }
 public static void apply(GunReloadEvent event,ResourceLocation id,int ammo,int capacity){
  if(ParasiteScopes.family(id)&&capacity>0&&ammo>=capacity)event.setCanceled(true);
 }
}
