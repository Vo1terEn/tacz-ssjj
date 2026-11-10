package cn.judgfire;

import java.util.ArrayDeque;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;

/** Optional Fantasy Ending negative-health-cap compatibility. No foreign classes are linked. */
public final class BarrierOffset {
  private static EntityDataAccessor<Float> key;
  private static final ThreadLocal<ArrayDeque<double[]>> OFFENSE = new ThreadLocal<>();

  private BarrierOffset() {}

  public static void enter(LivingEntity attacker) {
    double penetration = EnergyAttributes.penetration(attacker);
    var stack = OFFENSE.get();
    if (stack == null) OFFENSE.set(stack = new ArrayDeque<>());
    stack.push(new double[]{penetration,EnergyScaling.strengthMultiplier(EnergyAttributes.strength(attacker))});
  }

  public static void exit() {
    var stack = OFFENSE.get();
    if (stack == null) return;
    if (!stack.isEmpty()) stack.pop();
    if (stack.isEmpty()) OFFENSE.remove();
  }

  static double penetration() {
    var stack = OFFENSE.get();
    return stack == null || stack.isEmpty() ? 0 : stack.peek()[0];
  }

  /** Explicit foreign attacker scope; no recent-attacker inference. */
  static double strengthMultiplier() {
    var stack=OFFENSE.get();return stack==null||stack.isEmpty()?1:stack.peek()[1];
  }

  /** Scale only the new negative change, never the accumulated offset or its recovery. */
  public static float change(
      LivingEntity target, EntityDataAccessor<Float> accessor, float previous, float requested) {
    if (accessor != null) key = accessor;
    double multiplier = BarrierHealth.offsetMultiplier(target);
    if (multiplier == 1 || !Float.isFinite(previous) || requested >= previous) return requested;
    if (Float.isNaN(requested) || multiplier == 0) return previous;
    return (float) (previous - ((double) previous - requested) * multiplier);
  }

  /** Preserve the real cap in either transformation order; do not erase the remaining damage. */
  static float cap(LivingEntity target, float health) {
    var accessor = key;
    if (accessor == null || !target.getEntityData().hasItem(accessor)) return health;
    float offset = target.getEntityData().get(accessor);
    return Float.isNaN(offset) ? health : Math.min(health, target.getMaxHealth() + offset);
  }
}
