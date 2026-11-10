package cn.ssjjdimension;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.*;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DimensionDecoy {
 public record Decoy(UUID id,UUID owner,ResourceLocation dimension,Vec3 position,float yaw,long until,String skin,DimensionAttachments.Snapshot equipment){
  public Decoy(UUID id,UUID owner,ResourceLocation dimension,Vec3 position,float yaw,long until,String skin){this(id,owner,dimension,position,yaw,until,skin,DimensionAttachments.NONE);}
 }
 private record Dash(ItemStack weapon,int slot,ResourceLocation dimension,Vec3 direction,Vec3 origin,float yaw,long until,String skin){}
 private static final Map<UUID,Long> READY=new HashMap<>(),PROTECTED=new HashMap<>();
 private static final Map<UUID,Dash> DASHES=new HashMap<>();
 private static final Map<UUID,Decoy> DECOYS=new HashMap<>();
 private record Cloak(ServerPlayer player,boolean previousInvisible,long until){}
 private static final Map<UUID,Cloak> CLOAKS=new HashMap<>();
 private DimensionDecoy(){}
 public static int cooldown(ServerPlayer p){return remaining(READY.getOrDefault(p.getUUID(),0L),p.level().getGameTime());}
 public static int protection(ServerPlayer p){return remaining(PROTECTED.getOrDefault(p.getUUID(),0L),p.level().getGameTime());}
 public static int dashTicks(ServerPlayer p){var d=DASHES.get(p.getUUID());return d==null?0:remaining(d.until(),p.level().getGameTime());}
 private static int remaining(long until,long now){return (int)Math.max(0,Math.min(72000,until-now));}
 public static Decoy decoy(ServerPlayer p){return DECOYS.get(p.getUUID());}
 public static void request(ServerPlayer p,int slot){request(p,slot,1,0);}
 public static void request(ServerPlayer p,int slot,int forward,int left){
  if(p==null||forward< -1||forward>1||left< -1||left>1)return;
  if(!DimensionField.eligible(p,slot)||!DimensionDecoyRules.canDash(DimensionField.active(p),DimensionField.tier(p.getMainHandItem()),p.level().getGameTime(),READY.getOrDefault(p.getUUID(),0L))||p.isPassenger()||p.isSleeping()||p.isFallFlying())return;
  Vec3 flat=DimensionDecoyRules.direction(p.getYRot(),forward,left);if(flat.lengthSqr()<1e-8)return;
  long now=p.level().getGameTime();READY.put(p.getUUID(),now+DimensionDecoyRules.cooldownLimit());PROTECTED.put(p.getUUID(),now+DimensionDecoyRules.protectionDuration());
  DASHES.put(p.getUUID(),new Dash(p.getMainHandItem(),slot,p.level().dimension().location(),flat.normalize(),p.position(),p.getYRot(),now+DimensionDecoyRules.dashDuration(),DimensionVisualScope.skin(p.getMainHandItem())));
  remove(p);
  var clone=new Decoy(UUID.randomUUID(),p.getUUID(),p.level().dimension().location(),p.position(),p.getYRot(),now+DimensionDecoyRules.decoyDuration(),DimensionVisualScope.skin(p.getMainHandItem()),DimensionAttachments.capture(p.getMainHandItem()));
  DECOYS.put(p.getUUID(),clone);DimensionDecoyNetwork.visual((ServerLevel)p.level(),clone,0,DimensionDecoyRules.decoyDuration(),p.getId());
  CLOAKS.put(p.getUUID(),new Cloak(p,p.isInvisible(),now+DimensionDecoyRules.protectionDuration()));
  p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,DimensionDecoyRules.protectionDuration(),0,false,false,false));p.setInvisible(true);
  DimensionDecoyNetwork.cloak(p,DimensionDecoyRules.protectionDuration());
  for(var entity:p.level().getEntities(p,p.getBoundingBox().inflate(64),entity->entity instanceof Mob))if(entity instanceof Mob mob&&mob.getTarget()==p)mob.setTarget(null);
  pulse(p,DASHES.get(p.getUUID()));DimensionDecoyNetwork.status(p);
 }
 private static void finish(ServerPlayer p,boolean spawn){
  Dash d=DASHES.remove(p.getUUID());if(d==null)return;
  DimensionDecoyNetwork.status(p);
 }
 private static void remove(ServerPlayer p){
  var d=DECOYS.remove(p.getUUID());if(d!=null&&p.level() instanceof ServerLevel level&&d.dimension().equals(level.dimension().location()))DimensionDecoyNetwork.visual(level,d,1,0,p.getId());
 }
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
  if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
  long now=p.level().getGameTime();var d=DASHES.get(p.getUUID());
  if(d!=null){
   boolean bound=DimensionField.active(p)&&p.getMainHandItem()==d.weapon()&&p.getInventory().selected==d.slot()&&p.level().dimension().location().equals(d.dimension())&&!p.isPassenger();
   if(!bound)finish(p,p.isAlive()&&p.level().dimension().location().equals(d.dimension()));
   else if(now>=d.until())finish(p,true);
   else if(p.level() instanceof ServerLevel level){
    Vec3 next=p.position().add(d.direction().scale(DimensionDecoyRules.speed()));
    if(p.horizontalCollision||!level.hasChunkAt(BlockPos.containing(next)))finish(p,false);
    else pulse(p,d);
   }
  }
  var cloak=CLOAKS.get(p.getUUID());
  if(cloak!=null){if(now>=cloak.until())uncloak(p);else p.setInvisible(true);}

  var clone=DECOYS.get(p.getUUID());if(clone!=null&&(now>=clone.until()||!p.isAlive()))remove(p);
  if(READY.containsKey(p.getUUID())||DimensionOverload.active(p))if(now%5==0)DimensionDecoyNetwork.status(p);
  if(cooldown(p)==0)READY.remove(p.getUUID());if(protection(p)==0)PROTECTED.remove(p.getUUID());
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void reduce(LivingHurtEvent e){
  if(e.getEntity() instanceof ServerPlayer p&&protection(p)>0&&Float.isFinite(e.getAmount())&&e.getAmount()>0)e.setAmount(DimensionDecoyRules.protectedDamage(e.getAmount()));
 }
 /** React to the attempted hit, before shields, absorption, creative immunity and hurt cooldown. */
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void attack(LivingAttackEvent e){
  if(!e.isCanceled()&&Float.isFinite(e.getAmount())&&e.getAmount()>0&&e.getEntity() instanceof ServerPlayer p)retaliate(p,attacker(e.getSource()));
 }
 /** TaCZ carries an explicit shooter even when its later vanilla damage source omits one. */
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void gunAttack(com.tacz.guns.api.event.common.EntityHurtByGunEvent.Pre e){
  if(e.isCanceled()||e.getLogicalSide()!=net.minecraftforge.fml.LogicalSide.SERVER||!Float.isFinite(e.getBaseAmount())||e.getBaseAmount()<=0||e.getHurtEntity()==null)return;
  if(com.tacz.guns.entity.EntityKineticBullet.MaybeMultipartEntity.of(e.getHurtEntity()).core() instanceof ServerPlayer p)retaliate(p,e.getAttacker());
 }
 /** Keep a late fallback for integrations that post only the final damage event. */
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void attacked(LivingDamageEvent e){
  if(!e.isCanceled()&&Float.isFinite(e.getAmount())&&e.getAmount()>0&&e.getEntity() instanceof ServerPlayer p)retaliate(p,attacker(e.getSource()));
 }
 private static LivingEntity attacker(net.minecraft.world.damagesource.DamageSource source){
  if(source==null)return null;
  if(source.getEntity() instanceof LivingEntity living)return living;
  net.minecraft.world.entity.Entity entity=source.getDirectEntity();
  for(int depth=0;entity!=null&&depth<4;depth++){
   if(entity instanceof LivingEntity living)return living;
   if(entity instanceof net.minecraft.world.entity.projectile.Projectile projectile){var owner=projectile.getOwner();if(owner==entity)break;entity=owner;}else break;
  }
  return null;
 }
 private static void retaliate(ServerPlayer p,LivingEntity enemy){
  if(!(p.level() instanceof ServerLevel)||!p.isAlive()||enemy==null||enemy.level()!=p.level()||!DimensionField.counterEnemy(p,enemy))return;
  var d=DECOYS.get(p.getUUID());if(d==null||p.level().getGameTime()>=d.until()||!d.dimension().equals(p.level().dimension().location()))return;
  // Consume before damage callbacks: gun/attack/final-damage events cannot trigger the same clone twice.
  remove(p);if(DimensionField.decoyBlast(p,enemy,d.skin(),d.equipment())&&d.equipment().stock()){READY.remove(p.getUUID());DimensionDecoyNetwork.status(p);}
 }
 private static void pulse(ServerPlayer p,Dash d){
  var velocity=p.getDeltaMovement();p.setDeltaMovement(d.direction().x*DimensionDecoyRules.speed(),velocity.y,d.direction().z*DimensionDecoyRules.speed());
  p.hurtMarked=true;p.connection.send(new ClientboundSetEntityMotionPacket(p));
 }
 private static void uncloak(ServerPlayer p){
  var old=CLOAKS.remove(p.getUUID());if(old==null)return;
  p.setInvisible(old.previousInvisible()||p.hasEffect(MobEffects.INVISIBILITY));DimensionDecoyNetwork.cloak(p,0);
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void targeting(LivingChangeTargetEvent e){if(e.getNewTarget() instanceof ServerPlayer p&&protection(p)>0)e.setNewTarget(null);}
 @SubscribeEvent public static void mobTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){if(e.getEntity() instanceof Mob mob&&mob.getTarget() instanceof ServerPlayer p&&protection(p)>0)mob.setTarget(null);}
 @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer viewer&&e.getTarget() instanceof ServerPlayer target&&protection(target)>0)DimensionDecoyNetwork.cloakTo(target,viewer,protection(target));}
 private static void cleanup(ServerPlayer p){finish(p,false);remove(p);uncloak(p);PROTECTED.remove(p.getUUID());}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)cleanup(p);}
 @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)cleanup(p);}
 @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)cleanup(p);}
 @SubscribeEvent public static void stop(ServerStoppedEvent e){for(var cloak:CLOAKS.values())cloak.player().setInvisible(cloak.previousInvisible()||cloak.player().hasEffect(MobEffects.INVISIBILITY));CLOAKS.clear();READY.clear();PROTECTED.clear();DASHES.clear();DECOYS.clear();}
}
