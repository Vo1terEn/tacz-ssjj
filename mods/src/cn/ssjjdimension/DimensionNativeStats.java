package cn.ssjjdimension;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runs after native eval: replaces fresh caches, never mutates shared GunData. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DimensionNativeStats {
 private record Applied(AttachmentCacheProperty cache,long signature){}
 private static final Map<LivingEntity,Applied> APPLIED=Collections.synchronizedMap(new WeakHashMap<>());
 private DimensionNativeStats(){}
 public static void cache(AttachmentCacheProperty cache,ItemStack stack){
  if(DimensionField.tier(stack)<0)return;
  boolean muzzle=DimensionAttachments.capture(stack).muzzle();
  double damage=DimensionConfig.number(muzzle?"muzzle_pellet_damage":"pellet_damage")/(muzzle?8d:3d);
  if(damage!=1){var adjusted=new LinkedList<ExtraDamage.DistanceDamagePair>();for(var pair:cache.getCache(GunProperties.DAMAGE))adjusted.add(new ExtraDamage.DistanceDamagePair(pair.getDistance(),(float)(pair.getDamage()*damage)));cache.setCache(GunProperties.DAMAGE,adjusted);}
  double spread=DimensionConfig.number("spread_multiplier")*(muzzle?DimensionConfig.number("muzzle_spread_multiplier")/.5d:1);
  if(spread!=1){var adjusted=new EnumMap<InaccuracyType,Float>(InaccuracyType.class);cache.getCache(GunProperties.INACCURACY).forEach((kind,value)->adjusted.put(kind,(float)(value*spread)));cache.setCache(GunProperties.INACCURACY,adjusted);}
  int rpm=DimensionConfig.integer("rpm");if(rpm!=200)cache.setCache(GunProperties.ROUNDS_PER_MINUTE,rpm);
 }
 private static long signature(){long v=1;for(String key:List.of("pellet_damage","muzzle_pellet_damage","spread_multiplier","muzzle_spread_multiplier","rpm"))v=31*v+Double.doubleToLongBits(DimensionConfig.number(key));return v;}
 /** Login synchronization may arrive after native equip; refresh once when authority changes. */
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;var owner=e.player;var stack=owner.getMainHandItem();
  if(DimensionField.tier(stack)<0){APPLIED.remove(owner);return;}
  var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);var cache=IGunOperator.fromLivingEntity(owner).getCacheProperty();if(cache==null||gun==null)return;
  long signature=signature();var old=APPLIED.get(owner);
  if(old==null||old.cache()!=cache||old.signature()!=signature)TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).ifPresent(index->{cache.eval(stack,index.getGunData());APPLIED.put(owner,new Applied(cache,signature));});
 }
 @SubscribeEvent public static void stop(ServerStoppedEvent e){APPLIED.clear();}
}
