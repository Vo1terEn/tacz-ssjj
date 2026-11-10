package cn.judgfire;
import cn.fusionarsenal.config.ProgressionConfig;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Eligibility and installed magazine strength are intentionally separate. */
public final class FireBurstRules {
  private static final ResourceLocation MAG=new ResourceLocation("judgfire:red_lotus_burst_mag");
  private static final String SNAPSHOT="JudgfireBurstDamageMultiplier";
  private FireBurstRules() {}
  public static boolean second(ResourceLocation id) {
    return id!=null&&id.getNamespace().equals("judgfire")&&id.getPath().equals("judgment_flame");
  }
  public static boolean eligible(ItemStack stack) {
    var gun=IGun.getIGunOrNull(stack);return ProgressionConfig.enabled("fire.burst_enabled")&&gun!=null&&second(gun.getGunId(stack));
  }
  public static boolean installed(ItemStack stack) {
    var gun=IGun.getIGunOrNull(stack);
    return gun!=null&&second(gun.getGunId(stack))&&MAG.equals(gun.getAttachmentId(stack,AttachmentType.EXTENDED_MAG));
  }
  public static float multiplier(ItemStack stack) {return eligible(stack)?(float)ProgressionConfig.number(installed(stack)?"fire.magazine_burst_multiplier":"fire.burst_multiplier"):1f;}
  /** Called immediately after the existing activation snapshot, before equipment can change. */
  public static void capture(EntityKineticBullet bullet,ItemStack stack) {
    if(BurstRuntime.boosted(bullet))bullet.getPersistentData().putFloat(SNAPSHOT,multiplier(stack));
  }
  public static float shotMultiplier(EntityKineticBullet bullet) {
    if(!second(bullet.getGunId()))return 1f;
    float value=bullet.getPersistentData().getFloat(SNAPSHOT);
    return Float.isFinite(value)&&value>=0&&value<=100&&bullet.getPersistentData().contains(SNAPSHOT)?value:(float)ProgressionConfig.number("fire.burst_multiplier");
  }
  public static float crownDamage(boolean active,EntityKineticBullet bullet) {
    return CombatConfig.crownDamage()*(active?shotMultiplier(bullet):1f);
  }
  public static float crownFallback(boolean active,ItemStack stack) {
    return CombatConfig.crownDamage()*(active?multiplier(stack):1f);
  }
  /** Preserve the original native/property ratio and all attribute modifier operations. */
  public static float weapon(LivingEntity owner,float nativeDamage,boolean active,EntityKineticBullet bullet) {
    float configured=CombatConfig.weaponDamage()*(active?shotMultiplier(bullet):1f);
    return AttackBonuses.attributes(owner,AttackBonuses.finite((double)nativeDamage*configured/16d));
  }
}
