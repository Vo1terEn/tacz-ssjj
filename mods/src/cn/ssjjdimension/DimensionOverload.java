package cn.ssjjdimension;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Runtime weapon damage debuff; item NBT and gun definitions are never modified. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DimensionOverload {
 private static final UUID MODIFIER=UUID.fromString("b6733532-68fb-40ca-9ec4-e94cd5750110");
 private record State(ItemStack weapon,long start,double initial,boolean recovering){}
 private static final Map<LivingEntity,State> STATES=new WeakHashMap<>();
 private static final Map<EntityKineticBullet,Double> SHOTS=new WeakHashMap<>();
 private static final String SAVED="SSJJDimensionOverloadState",SHOT="SSJJDimensionOverloadShot";
 private DimensionOverload(){}
 public static void apply(LivingEntity entity){
  if(!(entity.level() instanceof ServerLevel)||!entity.isAlive())return;
  long now=entity.level().getGameTime();double prior=factor(entity);
  STATES.put(entity,new State(entity.getMainHandItem(),now,prior,false));
  entity.getPersistentData().putBoolean("SSJJDimensionOverload",true);
  refresh(entity,prior);save(entity);DimensionDecoyNetwork.overload(entity,true);
 }
 public static boolean active(LivingEntity e){return STATES.containsKey(e);}
 public static boolean recovering(LivingEntity e){var s=STATES.get(e);return s!=null&&s.recovering();}
 public static double factor(LivingEntity entity){
  var s=STATES.get(entity);if(s==null)return 1;
  long now=entity.level().getGameTime();
  double value=DimensionDecoyRules.factor(s.initial(),now-s.start(),s.recovering());
  if(!s.recovering()&&entity.getMainHandItem()!=s.weapon()){
   s=new State(entity.getMainHandItem(),now,value,true);STATES.put(entity,s);
  }
  if(s.recovering()&&now-s.start()>=DimensionDecoyRules.recoveryDuration()){clear(entity);return 1;}
  return value;
 }
 private static void refresh(LivingEntity e,double factor){
  var a=e.getAttribute(Attributes.ATTACK_DAMAGE);if(a==null)return;
  a.removeModifier(MODIFIER);
  if(factor<1)a.addTransientModifier(new AttributeModifier(MODIFIER,"Dimension overload",factor-1,AttributeModifier.Operation.MULTIPLY_TOTAL));
 }
 public static void clear(LivingEntity e){
  STATES.remove(e);var a=e.getAttribute(Attributes.ATTACK_DAMAGE);if(a!=null)a.removeModifier(MODIFIER);
  e.getPersistentData().remove("SSJJDimensionOverload");
  e.getPersistentData().remove(SAVED);
  if(e.level() instanceof ServerLevel)DimensionDecoyNetwork.overload(e,false);
 }
 @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;
  for(var target:new ArrayList<>(STATES.keySet())){
   if(!target.isAlive()||target.isRemoved()){clear(target);continue;}
   refresh(target,factor(target));if(active(target))save(target);
  }
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void projectile(EntityJoinLevelEvent e){
  if(e.getLevel().isClientSide())return;
  if(e.getEntity() instanceof LivingEntity living&&living.getPersistentData().contains(SAVED)&&!active(living)){
   var tag=living.getPersistentData().getCompound(SAVED);var expected=ItemStack.of(tag.getCompound("Weapon"));
   var s=new State(living.getMainHandItem(),tag.getLong("Start"),tag.getDouble("Initial"),tag.getBoolean("Recovering"));
   if(!s.recovering()&&!ItemStack.isSameItemSameTags(expected,living.getMainHandItem()))s=new State(living.getMainHandItem(),living.level().getGameTime(),DimensionDecoyRules.factor(s.initial(),living.level().getGameTime()-s.start(),false),true);
   STATES.put(living,s);refresh(living,factor(living));if(active(living))DimensionDecoyNetwork.overload(living,true);
  }
  if(e.getEntity() instanceof EntityKineticBullet b&&b.getOwner() instanceof LivingEntity owner){
   var tag=b.getPersistentData();double f=tag.contains(SHOT)?tag.getDouble(SHOT):factor(owner);
   f=Math.max(DimensionDecoyRules.minimumDamage(),Math.min(1,f));SHOTS.put(b,f);tag.putDouble(SHOT,f);
  }
  else if(e.getEntity() instanceof AbstractArrow a&&a.getOwner() instanceof LivingEntity owner){
   var tag=a.getPersistentData();if(!tag.contains(SHOT)){
    double f=factor(owner);tag.putDouble(SHOT,f);
    if(!(a instanceof ThrownTrident))a.setBaseDamage(a.getBaseDamage()*f);
   }
  }
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void gun(EntityHurtByGunEvent.Pre e){
  if(e.isCanceled()||e.getLogicalSide()!=LogicalSide.SERVER||e.getAttacker()==null)return;
  double f=e.getBullet() instanceof EntityKineticBullet b?SHOTS.getOrDefault(b,1d):factor(e.getAttacker());
  if(f<1)e.setBaseAmount((float)(e.getBaseAmount()*f));
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void trident(LivingHurtEvent e){
  if(e.getSource().getDirectEntity() instanceof ThrownTrident trident&&e.getSource().getEntity() instanceof LivingEntity owner){
   var tag=trident.getPersistentData();double f=tag.contains(SHOT)?tag.getDouble(SHOT):1;
   e.setAmount(Math.max(0,e.getAmount()-(float)(8*(1-f))));
  }
 }
 @SubscribeEvent public static void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking e){
  if(e.getTarget() instanceof LivingEntity target&&active(target)&&e.getEntity() instanceof net.minecraft.server.level.ServerPlayer viewer)DimensionDecoyNetwork.tracked(target,viewer);
 }
 @SubscribeEvent public static void death(LivingDeathEvent e){if(active(e.getEntity()))clear(e.getEntity());}
 @SubscribeEvent public static void leave(EntityLeaveLevelEvent e){
  if(e.getEntity() instanceof LivingEntity living&&active(living)){
   save(living);STATES.remove(living);refresh(living,1);DimensionDecoyNetwork.overload(living,false);
  }
  if(e.getEntity() instanceof EntityKineticBullet b)SHOTS.remove(b);
 }
 private static void save(LivingEntity e){
  var s=STATES.get(e);if(s==null)return;var tag=new CompoundTag();
  tag.put("Weapon",s.weapon().save(new CompoundTag()));tag.putLong("Start",s.start());tag.putDouble("Initial",s.initial());tag.putBoolean("Recovering",s.recovering());e.getPersistentData().put(SAVED,tag);
 }
 @SubscribeEvent public static void stop(ServerStoppedEvent e){for(var target:new ArrayList<>(STATES.keySet())){save(target);refresh(target,1);}STATES.clear();SHOTS.clear();}
}
