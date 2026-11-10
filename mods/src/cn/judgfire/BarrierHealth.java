package cn.judgfire;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server health boundary. The observation is tied to the vanilla data item, never a second life.
 */
public final class BarrierHealth {
  private BarrierHealth() {}

  private static EntityDataAccessor<Float> healthKey;
  private static final Map<Object, State> ITEMS = Collections.synchronizedMap(new WeakHashMap<>());
  private static final Map<LivingEntity, WeakReference<State>> OWNERS =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final ThreadLocal<ArrayDeque<Hit>> HITS = new ThreadLocal<>();
  private static final ThreadLocal<ArrayDeque<Write>> WRITES = new ThreadLocal<>();
  private static final ThreadLocal<ArrayDeque<WeakReference<LivingEntity>>> RESTORES =
      new ThreadLocal<>();

  private static final class State {
    final WeakReference<LivingEntity> owner;
    float observed;
    boolean busy;
    WeakReference<Object> item;

    State(LivingEntity owner, float value) {
      this.owner = new WeakReference<>(owner);
      observed = value;
    }
  }

  private static final class Hit {
    final WeakReference<LivingEntity> target;
    final WeakReference<DamageSource> source;
    final double penetration, resistance, strengthMultiplier;
    boolean energyApplied;
    Hit(WeakReference<LivingEntity> target, WeakReference<DamageSource> source,
        double penetration, double resistance, double strengthMultiplier) {
      this.target=target; this.source=source; this.penetration=penetration;
      this.resistance=resistance; this.strengthMultiplier=strengthMultiplier;
    }
  }

  private static Hit hit(LivingEntity target) {
    var stack = HITS.get();
    if (stack != null) for (var entry : stack) if (entry.target.get() == target) return entry;
    return null;
  }

  private static double resistance(LivingEntity target) {
    Hit current = hit(target);
    if (current != null) return current.resistance;
    Write write = write(target);
    return write == null ? EnergyAttributes.resistance(target) : write.resistance;
  }

  /** Event and commit use the same operation snapshot, including an inactive main hand. */
  static double difference(LivingEntity target, DamageSource source) {
    return difference(target,source,false);
  }

  /** A later fallback event is a fresh component, but uses the same entry snapshot. */
  static double eventDifference(LivingEntity target, DamageSource source) {
    return difference(target,source,true);
  }

  private static double difference(LivingEntity target, DamageSource source, boolean event) {
    Hit current = hit(target);
    if (current != null && (source == null || current.source.get() == source))
      return !event&&current.energyApplied?0:current.penetration - current.resistance;
    Write write = write(target);
    if (source == null && write != null) return write.difference;
    double penetration =
        source == null
            ? BarrierOffset.penetration()
            : source.getEntity() instanceof LivingEntity living
                ? EnergyAttributes.penetration(living)
                : 0;
    return penetration - EnergyAttributes.resistance(target);
  }

  /** Strength follows this exact hit snapshot, never the target's recent attacker. */
  static double strengthMultiplier(LivingEntity target, DamageSource source) {
    return strengthMultiplier(target,source,false);
  }

  static double eventStrengthMultiplier(LivingEntity target, DamageSource source) {
    return strengthMultiplier(target,source,true);
  }

  private static double strengthMultiplier(LivingEntity target, DamageSource source, boolean event) {
    Hit current=hit(target);
    if(current!=null&&(source==null||current.source.get()==source))
      return !event&&current.energyApplied?1:current.strengthMultiplier;
    Write currentWrite=write(target);
    if(source==null&&currentWrite!=null)return currentWrite.strengthMultiplier;
    return source!=null&&source.getEntity() instanceof LivingEntity living
        ?EnergyScaling.strengthMultiplier(EnergyAttributes.strength(living)):BarrierOffset.strengthMultiplier();
  }

  /** Compatibility name: an ordinary fallback has applied the ENTIRE energy multiplier. */
  static void strengthApplied(LivingEntity target, DamageSource source) {
    Hit current=hit(target);
    if(current!=null&&current.source.get()==source)current.energyApplied=true;
  }

  private static final class Write {
    final WeakReference<LivingEntity> target;
    final float expected;
    final double resistance, difference, strengthMultiplier;
    boolean consumed;
    boolean healing;
    boolean resolved;
    boolean override;
    float adjustedRequest;
    CommitPermit permit;

    Write(LivingEntity target, float expected, double resistance, double difference, double strengthMultiplier) {
      this.target = new WeakReference<>(target);
      this.expected = expected;
      this.resistance = resistance;
      this.difference = difference;
      this.strengthMultiplier = strengthMultiplier;
    }
  }

  private static Write write(LivingEntity target) {
    var stack = WRITES.get();
    if (stack != null) for (var entry : stack) if (entry.target.get() == target) return entry;
    return null;
  }

  @SuppressWarnings("unchecked")
  public static void registerHealth(Object key) {
    healthKey = (EntityDataAccessor<Float>) key;
  }

  public static void registerData(Entity entity, EntityDataAccessor<?> key, Object item) {
    if (key != healthKey
        || !(entity instanceof LivingEntity living)
        || !(item instanceof SynchedEntityData.DataItem<?> data)) return;
    State state = state(living);
    Object previous = state == null || state.item == null ? null : state.item.get();
    if (previous == item) return;
    if (!(data.getValue() instanceof Float value)) return;
    if (previous != null) ITEMS.remove(previous);
    if (state == null) state = new State(living, value);
    state.item = new WeakReference<>(item);
    ITEMS.put(item, state);
    OWNERS.put(living, new WeakReference<>(state));
  }

  private static State state(LivingEntity target) {
    var reference = OWNERS.get(target);
    return reference == null ? null : reference.get();
  }

  private static boolean server(LivingEntity target) {
    return target != null && target.level() != null && !target.level().isClientSide;
  }

  private static boolean restoring(LivingEntity target) {
    var stack = RESTORES.get();
    if (stack != null) for (var entry : stack) if (entry.get() == target) return true;
    return false;
  }

  /** Only defer events when the vanilla health observation has actually been registered. */
  static boolean defers(LivingEntity target) {
    State state = state(target);
    if (state == null || state.busy || !server(target) || restoring(target)) return false;
    state.busy = true;
    try {
      Hit current=hit(target);
      return (current==null||!current.energyApplied)
          && (resistance(target)>0 || offsetMultiplier(target)>1);
    } finally {
      state.busy = false;
    }
  }

  public static void enterDamage(LivingEntity target, DamageSource source) {
    enter(target, source, false);
  }

  public static void enterCommit(LivingEntity target, DamageSource source) {
    enter(target, source, true);
  }

  private static void enter(LivingEntity target, DamageSource source, boolean delegation) {
    var stack = HITS.get();
    if (stack == null) HITS.set(stack = new ArrayDeque<>());
    Hit current = hit(target);
    // hurt -> actuallyHurt is delegation of the same operation, not a new attribute read.
    if (delegation && current != null && current.source.get() == source) {
      stack.push(current);
    } else {
      double penetration =
          source != null && source.getEntity() instanceof LivingEntity living
              ? EnergyAttributes.penetration(living)
              : 0;
      double resistance = EnergyAttributes.resistance(target);
      double strengthMultiplier=source!=null&&source.getEntity() instanceof LivingEntity living
          ?EnergyScaling.strengthMultiplier(EnergyAttributes.strength(living)):1;
      stack.push(
          new Hit(
              new WeakReference<>(target), new WeakReference<>(source), penetration, resistance, strengthMultiplier));
    }
  }

  public static void exitDamage() {
    pop(HITS);
  }

  public static void beginRestore(LivingEntity target) {
    var stack = RESTORES.get();
    if (stack == null) RESTORES.set(stack = new ArrayDeque<>());
    stack.push(new WeakReference<>(target));
  }

  public static void endRestore() {
    pop(RESTORES);
  }

  private static <T> void pop(ThreadLocal<ArrayDeque<T>> local) {
    var stack = local.get();
    if (stack == null) return;
    if (!stack.isEmpty()) stack.pop();
    if (stack.isEmpty()) local.remove();
  }

  /** Capture the negative, pre-clamp request so a large lethal hit is not capped to current HP. */
  public static float beginHealth(LivingEntity target, float requested) {
    return beginHealth(target,requested,false);
  }

  /** Scale before an overriding setter records or intentionally declines a native commit. */
  public static float beginOverrideHealth(LivingEntity target, float requested) {
    return beginHealth(target,requested,true);
  }

  private static float beginHealth(LivingEntity target, float requested, boolean override) {
    Write parent=write(target);
    if(!override&&parent!=null&&parent.override
        &&(Float.floatToIntBits(requested)==Float.floatToIntBits(parent.adjustedRequest)
          ||Float.floatToIntBits(requested)==Float.floatToIntBits(parent.expected))){
      Write delegated=new Write(target,parent.expected,parent.resistance,parent.difference,parent.strengthMultiplier);
      delegated.healing=parent.healing;delegated.adjustedRequest=requested;
      var stack=WRITES.get();stack.push(delegated);
      return requested;
    }
    State state = state(target);
    double resistance = resistance(target);
    double difference = difference(target, null);
    float adjusted = requested;
    float before = requested;
    if (state != null && !state.busy) {
      // This read also reconciles a preceding direct field edit.
      float old = target.getEntityData().get(healthKey);
      if (server(target) && !restoring(target) && defers(target))
        old = BarrierOffset.cap(target, old);
      before = old;
      state.busy = true;
      try {
        adjusted = adjust(target, old, requested,override);
      } finally {
        state.busy = false;
      }
    }
    var stack = WRITES.get();
    if (stack == null) WRITES.set(stack = new ArrayDeque<>());
    Write write =
        new Write(
            target, Math.max(0, Math.min(target.getMaxHealth(), adjusted)), resistance, difference, strengthMultiplier(target,null));
    write.healing = Float.isFinite(requested) && requested > before && before > 0;
    write.override=override;write.adjustedRequest=adjusted;
    stack.push(write);
    return adjusted;
  }

  static EntityDataAccessor<Float> key() {
    return healthKey;
  }

  /** Exact precomputed native-pool commit, not a general immunity scope. */
  static void beginResolved(LivingEntity target, float expected) {
    float before = target.getEntityData().get(healthKey);
    Write write = new Write(target, expected, resistance(target), difference(target, null), strengthMultiplier(target,null));
    write.permit = CommitPermit.open(target, before, expected);
    write.resolved = true;
    var stack = WRITES.get();
    if (stack == null) WRITES.set(stack = new ArrayDeque<>());
    stack.push(write);
  }

  public static void endHealth() {
    var stack = WRITES.get();
    if (stack != null && !stack.isEmpty() && stack.peek().permit != null)
      stack.peek().permit.close();
    pop(WRITES);
  }

  private static void commit(LivingEntity target, State state, float value) {
    boolean busy = state.busy;
    state.busy = true;
    try {
      Object item = state.item == null ? null : state.item.get();
      if (item != null && BarrierStorage.write(target.getEntityData(), item, value))
        state.observed = value;
    } finally {
      state.busy = busy;
    }
  }

  private static float adjust(LivingEntity target, float old, float requested) {
    return adjust(target,old,requested,false);
  }

  private static float adjust(LivingEntity target, float old, float requested, boolean unbounded) {
    if (!server(target)
        || restoring(target)
        || !(old > 0)
        || !Float.isFinite(old)
        || requested >= old) return requested;
    double resistance = resistance(target);
    if (!(resistance > 0) && offsetMultiplier(target)<=1) return requested;
    old = BarrierOffset.cap(target, old);
    if (!(old > 0) || !Float.isFinite(old) || requested >= old) return requested;
    if (Float.isNaN(requested)) return old;
    double multiplier = offsetMultiplier(target);
    // Infinite raw damage remains lethal for any positive multiplier; never manufacture NaN.
    if (multiplier == 0) return old;
    double result=old-((double)old-requested)*multiplier;
    return (float)(unbounded?result:Math.max(0,result));
  }

  public static double offsetMultiplier(LivingEntity target) {
    if (!server(target) || restoring(target)) return 1;
    double strength=strengthMultiplier(target,null);
    return EnergyScaling.multiplier(difference(target, null))*strength;
  }

  /** Covers direct and reflective setter calls, including writes that skip setHealth. */
  public static Object dataHealth(Object item, Object requested) {
    State state = ITEMS.get(item);
    if (state == null || !(requested instanceof Float value) || state.busy) return requested;
    LivingEntity target = state.owner.get();
    if (target == null) return requested;
    state.busy = true;
    try {
      float baseline = state.observed;
      var writes = WRITES.get();
      if (writes != null)
        for (var write : writes) {
          if (write.target.get() != target) continue;
          if (write.healing) {
            break;
          }
          if (write.resolved) {
            if (CommitPermit.consume(target, baseline, value)) {
              baseline = value;
              write.consumed = true;
            }
            break;
          }
          // Only the already adjusted portion is exempt. Later injected extra loss is reduced too.
          if (!write.consumed) {
            baseline = write.expected;
            write.consumed = true;
          }
          break;
        }
      float result = adjust(target, baseline, value);
      state.observed = result;
      return result;
    } finally {
      state.busy = false;
    }
  }

  /** Reconcile an unobserved raw field write at the next standard read and mark it for sync. */
  public static Object readData(Object item, Object raw) {
    State state = ITEMS.get(item);
    if (state == null
        || !(raw instanceof Float value)
        || state.busy
        || Float.floatToIntBits(value) == Float.floatToIntBits(state.observed)) return raw;
    LivingEntity target = state.owner.get();
    if (target == null) return raw;
    state.busy = true;
    try {
      float baseline = state.observed;
      Write write = write(target);
      if (write != null && write.resolved && CommitPermit.consume(target, baseline, value)) {
        baseline = write.expected;
        write.consumed = true;
      }
      float result = adjust(target, baseline, value);
      state.observed = result;
      if (Float.floatToIntBits(result) != Float.floatToIntBits(value))
        commit(target, state, result);
      return result;
    } finally {
      state.busy = false;
    }
  }

  /** Ignore a changed getter result when the protected vanilla pool is still authoritative. */
  public static float readHealth(LivingEntity target, float returned) {
    State state = state(target);
    if (state == null || state.busy || !server(target) || restoring(target)) return returned;
    float stored = target.getEntityData().get(healthKey);
    if (defers(target)) return BarrierOffset.cap(target, stored);
    return returned;
  }
}
