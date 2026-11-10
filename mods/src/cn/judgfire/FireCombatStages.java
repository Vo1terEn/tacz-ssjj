package cn.judgfire;
import cn.fusionarsenal.config.ProgressionConfig;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Exact IDs and actual installed accessories; no inventory-based stage promotion. */
public final class FireCombatStages {
 private static final ResourceLocation STOCK=new ResourceLocation("judgfire:emperor_crown_stock"), GRIP=new ResourceLocation("judgfire:red_lotus_grip");
 private FireCombatStages(){}
 public static int tier(ResourceLocation id){
  if(id==null||!id.getNamespace().equals("judgfire"))return -1;
  return switch(id.getPath()){case "judgment_flame_base"->0;case "judgment_flame_tier1"->1;case "judgment_flame"->2;default->-1;};
 }
 public static int tier(ItemStack stack){var gun=IGun.getIGunOrNull(stack);return gun==null?-1:tier(gun.getGunId(stack));}
 public static boolean crown(ItemStack stack){return ProgressionConfig.enabled("fire.crown_enabled")&&tier(stack)>=0;}
 public static boolean lotus(ItemStack stack){return ProgressionConfig.enabled("fire.lotus_enabled")&&tier(stack)>=1;}
 private static boolean installed(ItemStack stack,AttachmentType slot,ResourceLocation attachment){var gun=IGun.getIGunOrNull(stack);return tier(stack)>=1&&gun!=null&&attachment.equals(gun.getAttachmentId(stack,slot));}
 public static double range(ItemStack stack){
  int tier=tier(stack);double base=tier<0?0:ProgressionConfig.fireRange(tier);
  return base*(installed(stack,AttachmentType.STOCK,STOCK)?ProgressionConfig.number("fire.stock_range_multiplier"):1);
 }
 public static double reduction(ItemStack stack){int t=tier(stack);if(t<1||!ProgressionConfig.enabled("fire.lotus_enabled"))return 0;double value=ProgressionConfig.number(t==1?"fire.lotus_reduction_tier1":"fire.lotus_reduction_tier2");return Math.min(1,value*(installed(stack,AttachmentType.GRIP,GRIP)?ProgressionConfig.number("fire.grip_reduction_multiplier"):1));}
 /** Preserve the original axis-based acquisition boundary, changing only its per-gun extent. */
 public static boolean inRange(double x,double y,double z,LivingEntity owner){
  double r=range(owner.getMainHandItem());return r>0&&Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z)))<=r;
 }
 public static float damageScale(ResourceLocation gun){int t=tier(gun);return t<0?1f:(float)ProgressionConfig.fireDamage(t);}
 public static float gunDamage(float damage,EntityKineticBullet bullet){return CrownAttack.crown(bullet)?damage:damage*damageScale(bullet.getGunId());}
 public static float crownDamage(float damage,ItemStack stack){var gun=IGun.getIGunOrNull(stack);return gun==null?damage:damage*damageScale(gun.getGunId(stack));}
}
