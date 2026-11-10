package cn.judgfire;

import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Apply energy attributes to the ordinary final damage event, without another damage path. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class EnergyScaling {
  private EnergyScaling() {}

  static double multiplier(double difference) {
    if (Double.isNaN(difference)) return 1;
    if (difference < 0) return 100 / (100 - difference);
    double squared = difference * difference;
    return Double.isInfinite(squared) ? 2 : 1 + squared / (squared + 250000);
  }

  /** Extra damage is additive per thousand strength; malformed attributes stay neutral. */
  static double strengthMultiplier(double strength) {
    double coefficient=cn.fusionarsenal.config.ArsenalConfig.number("combat.strength_damage_per_1000");
    if(!(strength>0)||!Double.isFinite(strength)||!(coefficient>0)||!Double.isFinite(coefficient))return 1;
    double value=1+(strength/1000)*coefficient;
    return Double.isFinite(value)?value:Double.MAX_VALUE;
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void damage(LivingDamageEvent event) {
    if(event.getSource() instanceof cn.ssjjparasite2.client.ParasiteDestructionDamage)return;
    float amount = event.getAmount();
    if (event.isCanceled()
        || !(amount > 0)
        || !Float.isFinite(amount)
        || event.getEntity().level().isClientSide
        || AttackBonuses.calculating(event)
) return;
    double difference = BarrierHealth.eventDifference(event.getEntity(), event.getSource());
    double scaled = amount * multiplier(difference) * BarrierHealth.eventStrengthMultiplier(event.getEntity(),event.getSource());
    event.setAmount((float) Math.min(Float.MAX_VALUE, scaled));
    BarrierHealth.strengthApplied(event.getEntity(),event.getSource());
  }
}
