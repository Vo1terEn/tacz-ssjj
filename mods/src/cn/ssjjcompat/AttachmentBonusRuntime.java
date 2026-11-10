package cn.ssjjcompat;
import com.tacz.guns.api.*;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Flat base-stat additions before the existing native attack, energy and armor pipelines. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class AttachmentBonusRuntime {
 private static final String SHOT="SSJJOrdinaryAttachmentBonus";
 private static final Set<String> EYE=Set.of("notdistinguish_base","notdistinguish_tier1","notdistinguish");
 private static final Set<String> FIRE=Set.of("judgment_flame_base","judgment_flame_tier1","judgment_flame");
 private static final Set<String> PARASITE=Set.of("parasitic_sniper","parasitic_sniper1","parasitic_sniper2");
 private static final Set<String> DIMENSION=Set.of("dimension_star","dimension_star1","dimension_star2");
 private record Applied(AttachmentCacheProperty cache,double bonus){}
 private static final Map<LivingEntity,Applied> APPLIED=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<Object,Double> EYE_SKILLS=Collections.synchronizedMap(new WeakHashMap<>());
 private AttachmentBonusRuntime(){}
 public static String family(ResourceLocation id){
  if(id==null)return "";String path=id.getPath();return switch(id.getNamespace()){
   case "eyeofpunishment"->EYE.contains(path)?"eye":"";
   case "judgfire"->FIRE.contains(path)?"fire":"";
   case "ssjjparasite2"->PARASITE.contains(path)?"parasite":"";
   case "ssjj_dimension"->DIMENSION.contains(path)?"dimension":"";default->"";};
 }
 public static String family(ItemStack stack){var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);return gun==null?"":family(gun.getGunId(stack));}
 /** Count real supported slots only; forged NBT, wrong-slot IDs and cosmetic slots give nothing. */
 public static int count(ItemStack stack){
  if(family(stack).isEmpty())return 0;var gun=IGun.getIGunOrNull(stack);int count=0;
  for(var slot:List.of(AttachmentType.MUZZLE,AttachmentType.STOCK,AttachmentType.GRIP,AttachmentType.EXTENDED_MAG)){
   if(!gun.allowAttachmentType(stack,slot))continue;var id=gun.getAttachmentId(stack,slot);
   if(id==null||!TimelessAPI.getCommonAttachmentIndex(id).map(i->i.getType()==slot).orElse(false)||!AllowAttachmentTagMatcher.match(gun.getGunId(stack),id))continue;
   count++;
  }return count;
 }
 public static double energy(double original,ItemStack stack,boolean penetration){
  int n=count(stack);return original+n*switch(family(stack)){
   case "fire"->penetration?0:AttachmentBonusConfig.value("judgment_energy_resistance");
   case "dimension"->AttachmentBonusConfig.value(penetration?"dimension_energy_penetration":"dimension_energy_resistance");default->0;};
 }
 public static double strength(double original,ItemStack stack){return original+(family(stack).equals("dimension")?count(stack)*AttachmentBonusConfig.value("dimension_energy_strength"):0);}
 public static double eyeDamage(double original,ItemStack stack){return original;}
 public static void captureEyeSkill(Object skill,ItemStack stack){EYE_SKILLS.put(skill,eyeDamage(0,stack));}
 public static void inheritEyeSkill(Object cloud,Object ball){EYE_SKILLS.put(cloud,EYE_SKILLS.getOrDefault(ball,0d));}
 public static double eyeCloudDamage(double original,Object cloud){return original+EYE_SKILLS.getOrDefault(cloud,0d);}
 /** Called only after native eval has rebuilt a fresh distance table. */
 public static void cache(AttachmentCacheProperty cache,ItemStack stack){
  if(!family(stack).equals("eye"))return;double bonus=eyeDamage(0,stack);if(bonus==0)return;
  var adjusted=new LinkedList<ExtraDamage.DistanceDamagePair>();for(var pair:cache.getCache(GunProperties.DAMAGE))adjusted.add(new ExtraDamage.DistanceDamagePair(pair.getDistance(),(float)(pair.getDamage()+bonus)));cache.setCache(GunProperties.DAMAGE,adjusted);
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST)public static void launch(EntityJoinLevelEvent event){
  if(event.getLevel().isClientSide()||!(event.getEntity() instanceof EntityKineticBullet bullet)||!family(bullet.getGunId()).equals("parasite"))return;
  var tag=bullet.getPersistentData();if(tag.contains(SHOT))return;var snapshot=new CompoundTag();int n=0;
  if(bullet.getOwner() instanceof LivingEntity owner){var stack=owner.getMainHandItem();var gun=IGun.getIGunOrNull(stack);if(gun!=null&&bullet.getGunId().equals(gun.getGunId(stack)))n=count(stack);}
  snapshot.putDouble("Bullet",n*AttachmentBonusConfig.value("parasite_bullet_damage"));snapshot.putDouble("Wave",n*AttachmentBonusConfig.value("parasite_wave_damage"));tag.put(SHOT,snapshot);
 }
 /** ParasiteCannon's own source-damage selection has already occurred; native multipliers still follow. */
 public static float parasiteDamage(float original,ResourceLocation gun,CompoundTag shot){
  if(!family(gun).equals("parasite")||!shot.contains(SHOT))return original;
  double bonus=shot.getCompound(SHOT).getDouble(shot.getBoolean(cn.ssjjparasite2.client.ParasiteCannon.BULLET)?"Wave":"Bullet");
  return (float)Math.min(Float.MAX_VALUE,original+bonus);
 }
 /** Initial client authority can arrive after equip. Re-evaluate rather than repeatedly adding. */
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;var owner=e.player;var stack=owner.getMainHandItem();if(!family(stack).equals("eye")){APPLIED.remove(owner);return;}
  var gun=IGun.getIGunOrNull(stack);var cache=IGunOperator.fromLivingEntity(owner).getCacheProperty();if(cache==null)return;double bonus=eyeDamage(0,stack);var old=APPLIED.get(owner);
  if(old==null||old.cache()!=cache||old.bonus()!=bonus)TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).ifPresent(i->{cache.eval(stack,i.getGunData());APPLIED.put(owner,new Applied(cache,bonus));});
 }
 @SubscribeEvent public static void stop(ServerStoppedEvent e){APPLIED.clear();EYE_SKILLS.clear();}
}
