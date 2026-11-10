package cn.judgfire;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.LogicalSide;
import org.apache.commons.lang3.tuple.Pair;

/** Shared native gun modifiers and TaCZ event context; Forge hit events belong to native hurt. */
public final class AttackBonuses {
  private static final ThreadLocal<Set<Event>> CALCULATING = new ThreadLocal<>();
  private static final ThreadLocal<Set<LivingEntity>> TARGETS = new ThreadLocal<>();

  static boolean calculating(Event event) {
    var events = CALCULATING.get();
    return events != null && events.contains(event);
  }

  static boolean busy(LivingEntity target) {
    var targets = TARGETS.get();
    return targets != null && targets.contains(target);
  }

  private static void post(Event event) {
    var events = CALCULATING.get();
    if (events == null) {
      events = Collections.newSetFromMap(new IdentityHashMap<>());
      CALCULATING.set(events);
    }
    events.add(event);
    try {
      MinecraftForge.EVENT_BUS.post(event);
    } finally {
      events.remove(event);
      if (events.isEmpty()) CALCULATING.remove();
    }
  }

  static float finite(double value) {
    return Double.isFinite(value) ? (float) Math.max(0, Math.min(Float.MAX_VALUE, value)) : 0;
  }

  /** Keep the gun's native property/cache modifiers; its unchanged pack baseline is 16. */
  static float weapon(LivingEntity owner, float nativeDamage, boolean burst) {
    float configured = BurstTiming.weaponDamage(CombatConfig.weaponDamage(), burst);
    return attributes(owner, finite((double) nativeDamage * configured / 16.0));
  }

  static float gun(LivingEntity owner, EntityKineticBullet bullet, float nativeDamage) {
    return CrownAttack.crown(bullet)
        ? attributes(
            owner,
            finite((double) nativeDamage * CrownAttack.base(BurstRuntime.boosted(bullet)) / 16.0))
        : weapon(owner, nativeDamage, BurstRuntime.boosted(bullet));
  }

  static float attributes(LivingEntity owner, float base) {
    return modifiers(owner == null ? null : owner.getAttribute(Attributes.ATTACK_DAMAGE), base);
  }

  /** Substitute the configured attack base, retaining vanilla modifier operations and UUIDs. */
  static float modifiers(AttributeInstance attribute, float base) {
    if (!Float.isFinite(base) || base <= 0) return 0;
    if (attribute == null) return base;
    double added = base;
    for (var modifier : attribute.getModifiers(AttributeModifier.Operation.ADDITION))
      added += modifier.getAmount();
    double result = added;
    for (var modifier : attribute.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE))
      result += added * modifier.getAmount();
    for (var modifier : attribute.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL))
      result *= 1 + modifier.getAmount();
    return finite(result);
  }

  /** Ordinary TaCZ pre stage for crown shots, honoring cancellation and modified amounts. */
  static float bullet(
      EntityKineticBullet bullet,
      LivingEntity owner,
      LivingEntity target,
      DamageSource source,
      float nativeDamage) {
    float floor = gun(owner, bullet, nativeDamage);
    if (floor <= 0 || busy(target)) return 0;
    begin(target);
    try {
      EntityHurtByGunEvent.Pre event =
          new EntityHurtByGunEvent.Pre(
              bullet,
              target,
              owner,
              bullet.getGunId(),
              bullet.getGunDisplayId(),
              nativeDamage,
              Pair.of(source, source),
              false,
              1,
              LogicalSide.SERVER);
      post(event);
      if (event.isCanceled()) return 0;
      return finite(event.getBaseAmount());
    } finally {
      end(target);
    }
  }

  private static void begin(LivingEntity target) {
    var targets = TARGETS.get();
    if (targets == null) {
      targets = Collections.newSetFromMap(new IdentityHashMap<>());
      TARGETS.set(targets);
    }
    targets.add(target);
  }

  private static void end(LivingEntity target) {
    var targets = TARGETS.get();
    targets.remove(target);
    if (targets.isEmpty()) TARGETS.remove();
  }

  static float direct(LivingEntity target, DamageSource source, float amount) {
    if (!Float.isFinite(amount) || amount <= 0 || busy(target)) return 0;
    begin(target);
    try {
      post(new JudgmentEvents.Attack(target, source, amount));
      var hurt = new JudgmentEvents.Hurt(target, source, amount);
      post(hurt);
      var damage = new JudgmentEvents.Damage(target, source, hurt.getAmount());
      post(damage);
      return damage.getAmount();
    } finally {
      end(target);
    }
  }

  static void bulletHit(
      EntityKineticBullet bullet,
      LivingEntity target,
      LivingEntity owner,
      DamageSource source,
      double loss) {
    if (loss > 0)
      post(
          new EntityHurtByGunEvent.Post(
              bullet,
              target,
              owner,
              bullet.getGunId(),
              bullet.getGunDisplayId(),
              finite(loss),
              Pair.of(source, source),
              false,
              1,
              LogicalSide.SERVER));
  }
}
