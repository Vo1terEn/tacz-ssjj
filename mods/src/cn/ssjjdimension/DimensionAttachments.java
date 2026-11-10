package cn.ssjjdimension;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Immutable effect selection: every delayed hit keeps the equipment that created it. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DimensionAttachments {
 public record Snapshot(boolean muzzle,boolean grip,boolean stock,boolean ultimate,boolean ability) {
  public float radius(int tier){return DimensionFieldRules.radius(tier)*(float)(grip?DimensionConfig.number("grip_radius_multiplier"):1);}
  public int duration(){return DimensionFieldRules.fieldDuration()+(grip?DimensionConfig.integer("grip_extra_ticks"):0);}
  public int markSeconds(int tier){return DimensionConfig.markSeconds(tier)*(muzzle?DimensionConfig.integer("muzzle_mark_duration_multiplier"):1);}
  public int stun(){return DimensionFieldRules.stunTicks()+(ultimate?DimensionConfig.integer("ultimate_extra_stun_ticks"):0);}
  public CompoundTag save(){var tag=new CompoundTag();tag.putBoolean("Muzzle",muzzle);tag.putBoolean("Grip",grip);tag.putBoolean("Stock",stock);tag.putBoolean("Ultimate",ultimate);tag.putBoolean("Ability",ability);return tag;}
 }
 public static final Snapshot NONE=new Snapshot(false,false,false,false,false);
 private static final String SHOT="SSJJDimensionAttachments";
 private static final Map<EntityKineticBullet,Snapshot> SHOTS=new WeakHashMap<>();
 private static final Map<EntityHurtByGunEvent.Pre,Float> GUN_BOOSTS=new WeakHashMap<>();
 private DimensionAttachments(){}
 private static boolean family(ResourceLocation id){return id!=null&&"ssjj_dimension".equals(id.getNamespace())&&List.of("dimension_star","dimension_star1","dimension_star2").contains(id.getPath());}
 private static boolean equipped(IGun gun,ItemStack stack,AttachmentType slot,String id){return new ResourceLocation(id).equals(gun.getAttachmentId(stack,slot))&&gun.allowAttachmentType(stack,slot)&&com.tacz.guns.util.AllowAttachmentTagMatcher.match(gun.getGunId(stack),new ResourceLocation(id));}
 public static Snapshot capture(ItemStack stack){
  int tier=DimensionField.tier(stack);var gun=tier<0?null:IGun.getIGunOrNull(stack);if(gun==null)return NONE;
  boolean muzzle=equipped(gun,stack,AttachmentType.MUZZLE,"ssjj_dimension:dimension_muzzle");
  boolean grip=tier>=1&&equipped(gun,stack,AttachmentType.GRIP,"ssjj_dimension:dimension_grip");
  boolean stock=tier>=2&&equipped(gun,stack,AttachmentType.STOCK,"ssjj_dimension:dimension_stock");
  boolean fusion=tier>=2&&(equipped(gun,stack,AttachmentType.LASER,"ssjj_dimension:fusion_upgrade_stone")||equipped(gun,stack,AttachmentType.LASER,"ssjjrpg:creative_upgrade_stone"));
  boolean ultimate=fusion||tier>=2&&equipped(gun,stack,AttachmentType.LASER,"ssjj_dimension:ultimate_upgrade_stone");
  boolean ability=fusion||tier>=2&&equipped(gun,stack,AttachmentType.LASER,"ssjj_dimension:ability_upgrade_stone");
  return new Snapshot(muzzle,grip,stock,ultimate,ability);
 }
 public static Snapshot load(CompoundTag tag){return new Snapshot(tag.getBoolean("Muzzle"),tag.getBoolean("Grip"),tag.getBoolean("Stock"),tag.getBoolean("Ultimate"),tag.getBoolean("Ability"));}
 public static Snapshot shot(EntityKineticBullet bullet){return SHOTS.getOrDefault(bullet,bullet.getPersistentData().contains(SHOT)?load(bullet.getPersistentData().getCompound(SHOT)):NONE);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void launch(EntityJoinLevelEvent e){
  if(!(e.getLevel() instanceof ServerLevel)||!(e.getEntity() instanceof EntityKineticBullet bullet)||!family(bullet.getGunId()))return;
  var tag=bullet.getPersistentData();if(tag.contains(SHOT)){SHOTS.putIfAbsent(bullet,load(tag.getCompound(SHOT)));return;}
  Snapshot state=NONE;if(bullet.getOwner() instanceof LivingEntity owner){var stack=owner.getMainHandItem();var gun=IGun.getIGunOrNull(stack);if(gun!=null&&bullet.getGunId().equals(gun.getGunId(stack)))state=capture(stack);}
  SHOTS.putIfAbsent(bullet,state);tag.put(SHOT,state.save());
 }
 public static float boost(Snapshot equipment,LivingEntity target){if(!equipment.ability()||target==null)return 1f;DimensionOverload.factor(target);return DimensionOverload.active(target)?(float)DimensionConfig.number("ability_damage_multiplier"):1f;}
 public static float gunBoost(EntityHurtByGunEvent.Pre e){return GUN_BOOSTS.getOrDefault(e,1f);}
 @SubscribeEvent(priority=EventPriority.HIGH) public static void gun(EntityHurtByGunEvent.Pre e){
  if(e.isCanceled()||e.getLogicalSide()!=LogicalSide.SERVER||!family(e.getGunId())||!Float.isFinite(e.getBaseAmount())||e.getBaseAmount()<=0||GUN_BOOSTS.containsKey(e))return;
  Snapshot equipment=NONE;
  if(e.getBullet() instanceof EntityKineticBullet bullet)equipment=shot(bullet);
  else if(e.getAttacker()!=null){var held=e.getAttacker().getMainHandItem();var gun=IGun.getIGunOrNull(held);if(gun!=null&&e.getGunId().equals(gun.getGunId(held)))equipment=capture(held);}
  var core=e.getHurtEntity()==null?null:EntityKineticBullet.MaybeMultipartEntity.of(e.getHurtEntity()).core();
  float factor=core instanceof LivingEntity living?boost(equipment,living):1;
  GUN_BOOSTS.put(e,factor);if(factor>1)e.setBaseAmount(e.getBaseAmount()*factor);
 }
 /** Only native direct melee attacks use this path; gun and skill paths are handled explicitly. */
 @SubscribeEvent(priority=EventPriority.HIGH) public static void melee(LivingHurtEvent e){
  if(e.isCanceled()||!(e.getEntity().level() instanceof ServerLevel)||!Float.isFinite(e.getAmount())||e.getAmount()<=0||!(e.getSource().getEntity() instanceof LivingEntity owner)||e.getSource().getDirectEntity()!=owner)return;
  if(!e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)&&!e.getSource().is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK))return;
  if(DimensionField.tier(owner.getMainHandItem())<0)return;
  e.setAmount(e.getAmount()*(float)(DimensionConfig.number("melee_damage")/15d)*boost(capture(owner.getMainHandItem()),e.getEntity()));
 }
 @SubscribeEvent public static void leave(EntityLeaveLevelEvent e){if(e.getEntity() instanceof EntityKineticBullet bullet)SHOTS.remove(bullet);}
 @SubscribeEvent public static void stop(ServerStoppedEvent e){SHOTS.clear();GUN_BOOSTS.clear();}
}
