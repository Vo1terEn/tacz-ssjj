package cn.punishmenteye;
import java.util.UUID;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
/** Server-only refund to the originating equipped gun, including clouds after switching slots. */
public final class MuzzleRefund {
 static final String KEY="eyeofpunishment_nofa_weapon";
 public static UUID identify(ServerPlayer p,ItemStack stack){
  var tag=stack.getOrCreateTag();UUID id=tag.hasUUID(KEY)?tag.getUUID(KEY):UUID.randomUUID();
  for(int i=0;i<p.getInventory().getContainerSize();i++){var other=p.getInventory().getItem(i);if(other!=stack&&other.hasTag()&&other.getTag().hasUUID(KEY)&&other.getTag().getUUID(KEY).equals(id)){id=UUID.randomUUID();break;}}
  tag.putUUID(KEY,id);return id;
 }
 public static void hit(ServerPlayer owner,UUID source,boolean acceptedDamage){
  if(!acceptedDamage||source==null||!owner.isAlive())return;
  for(int i=0;i<owner.getInventory().getContainerSize();i++){
   var s=owner.getInventory().getItem(i);if(!s.hasTag()||!s.getTag().hasUUID(KEY)||!source.equals(s.getTag().getUUID(KEY))||!NofaPower.installed(s))continue;
   var gun=(IGun)s.getItem();int old=gun.getCurrentAmmoCount(s),next=AmmoRefundRules.afterHit(old,true);if(next!=old){gun.setCurrentAmmoCount(s,next);owner.getInventory().setChanged();owner.inventoryMenu.broadcastChanges();if(owner.containerMenu!=owner.inventoryMenu)owner.containerMenu.broadcastChanges();}return;
  }
 }
}
