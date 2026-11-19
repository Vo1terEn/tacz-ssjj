package cn.judgfire;

import cn.ssjjdark.DarkLiberation;
import cn.ssjjdimension.DimensionEnergyGrowth;
import cn.ssjjparasite2.client.ParasiteDestructionDamage;
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
  private static final ThreadLocal<ArrayDeque<Restore>> RESTORES = new ThreadLocal<>();

  private record Restore(LivingEntity target, Hit hit) {}

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
    boolean energyApplied, defenseDeferred;
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
    if (current != null && (source == null || current.source.get() == source)) {
      double difference = current.penetration - current.resistance;
      if (event || !current.energyApplied) return difference;
      return current.defenseDeferred ? Math.min(0, difference) : 0;
    }
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

  static boolean defenseAtCommit(LivingEntity target, DamageSource source) {
    Hit current = hit(target);
    State state = state(target);
    return current != null && current.source.get() == source && state != null && !state.busy
        && server(target) && !restoring(target);
  }

  /** Record which portion the event handled, so the final commit can still protect the receiver. */
  static void energyApplied(LivingEntity target, DamageSource source, boolean defenseDeferred) {
    Hit current=hit(target);
    if (current != null && current.source.get() == source) {
      current.energyApplied = true;
      current.defenseDeferred = defenseDeferred;
    }
  }

  static void strengthApplied(LivingEntity target, DamageSource source) {
    energyApplied(target, source, false);
  }

  private static final class Write {
    final WeakReference<LivingEntity> target;
    final float expected;
    final double resistance, difference, strengthMultiplier;
    boolean consumed;
    boolean healing;
    boolean resolved;
    boolean tracked;
    boolean override;
    float before;
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

  /** Apply the defensive side to an attack that bypasses the normal hurt path. */
  public static float defendDirect(LivingEntity target, DamageSource source, float damage, boolean fixed) {
    if (!server(target) || !Float.isFinite(damage) || damage <= 0) return damage;
    double difference = fixed || source instanceof ParasiteDestructionDamage
        ? -EnergyAttributes.resistance(target) : eventDifference(target, source);
    return difference < 0 ? (float) (damage * EnergyScaling.multiplier(difference)) : damage;
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
    if (stack != null) {
      Hit current = hit(target);
      for (var entry : stack) if (entry.target == target && entry.hit == current) return true;
    }
    return false;
  }

  /** Only defer events when the vanilla health observation has actually been registered. */
  static boolean defers(LivingEntity target) {
    State state = state(target);
    if (state == null || state.busy || !server(target) || restoring(target)) return false;
    state.busy = true;
    try {
      Hit current=hit(target);
      return (current==null||!current.energyApplied||current.defenseDeferred)
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
    DimensionEnergyGrowth.enter(target, source, delegation);
    try {
      enterSnapshot(target, source, delegation);
    } catch (RuntimeException | Error failure) {
      DimensionEnergyGrowth.exit();
      throw failure;
    }
  }

  private static void enterSnapshot(LivingEntity target, DamageSource source, boolean delegation) {
    Hit current = hit(target);
    // hurt -> actuallyHurt is delegation of the same operation, not a new attribute read.
    if (!delegation || current == null || current.source.get() != source) {
      LivingEntity owner = source != null && source.getEntity() instanceof LivingEntity living
          && !(source instanceof ParasiteDestructionDamage) ? living : null;
      double penetration = owner == null ? 0 : EnergyAttributes.penetration(owner);
      double strengthMultiplier = owner == null ? 1 : EnergyScaling.strengthMultiplier(EnergyAttributes.strength(owner));
      current = new Hit(new WeakReference<>(target), new WeakReference<>(source), penetration,
          EnergyAttributes.resistance(target), strengthMultiplier);
    }
    var stack = HITS.get();
    if (stack == null) HITS.set(stack = new ArrayDeque<>());
    stack.push(current);
  }

  public static void exitDamage() {
    try { DimensionEnergyGrowth.exit(); }
    finally { pop(HITS); }
  }

  public static void beginRestore(LivingEntity target) {
    var stack = RESTORES.get();
    if (stack == null) RESTORES.set(stack = new ArrayDeque<>());
    stack.push(new Restore(target, hit(target)));
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
    if(!override&&parent!=null&&parent.override&&!parent.consumed
        &&(Float.floatToIntBits(requested)==Float.floatToIntBits(parent.adjustedRequest)
          ||Float.floatToIntBits(requested)==Float.floatToIntBits(parent.expected))){
      parent.consumed = true;
      Write delegated = new Write(target, parent.expected, parent.resistance, parent.difference, parent.strengthMultiplier);
      delegated.healing = parent.healing;
      delegated.adjustedRequest = requested;
      delegated.before = parent.before;
      delegated.tracked = parent.tracked;
      WRITES.get().push(delegated);
      return requested;
    }
    State state = state(target);
    double resistance = resistance(target);
    double difference = difference(target, null);
    float adjusted = requested;
    float before = requested;
    boolean tracked = state != null && !state.busy;
    if (tracked) {
      // This read also reconciles a preceding direct field edit.
      float old = target.getEntityData().get(healthKey);
      before = old;
      if (server(target) && !restoring(target) && defers(target))
        old = BarrierOffset.cap(target, old);
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
    write.before = before;
    write.tracked = tracked;
    write.override=override;write.adjustedRequest=adjusted;
    stack.push(write);
    return adjusted;
  }

  static EntityDataAccessor<Float> key() {
    return healthKey;
  }

  /** Exact precomputed native-pool commit, not a general immunity scope. */
  static void beginResolved(LivingEntity target, float expected) {
    beginResolved(target, target.getEntityData().get(healthKey), expected);
  }

  private static void beginResolved(LivingEntity target, float before, float expected) {
    Write write = new Write(target, expected, resistance(target), difference(target, null), strengthMultiplier(target,null));
    write.permit = CommitPermit.open(target, before, expected);
    write.resolved = true;
    var stack = WRITES.get();
    if (stack == null) WRITES.set(stack = new ArrayDeque<>());
    stack.push(write);
  }

  /** Permit only the already-defended vanilla pool write during a proven-pool transaction. */
  public static void commitResolved(LivingEntity target, float expected, Runnable action) {
    var data = target.getEntityData();
    if (healthKey == null || !data.hasItem(healthKey)) {
      action.run();
      return;
    }
    float before = data.get(healthKey);
    if (!Float.isFinite(before) || before < expected) {
      action.run();
      return;
    }
    beginResolved(target, before, expected);
    try { action.run(); }
    finally { endHealth(); }
  }

  static void mirrored(LivingEntity target, Object item, float value) {
    registerData(target, healthKey, item);
    State state = ITEMS.get(item);
    if (state != null && state.owner.get() == target) state.observed = value;
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
    boolean restore = restoring(target);
    float result = requested;
    if (server(target) && !restore && old > 0 && Float.isFinite(old) && !(requested >= old)) {
      double multiplier = offsetMultiplier(target);
      if (resistance(target) > 0 || multiplier > 1) {
        old = BarrierOffset.cap(target, old);
        if (old > 0 && Float.isFinite(old) && !(requested >= old)) {
          if (Float.isNaN(requested) || multiplier == 0) result = old;
          else {
            double reduced = old - ((double) old - requested) * multiplier;
            result = (float) (unbounded ? reduced : Math.max(0, reduced));
          }
        }
      }
    }
    return DarkLiberation.healthBoundary(target, old, result, restore);
  }

  public static double offsetMultiplier(LivingEntity target) {
    if (!server(target) || restoring(target)) return 1;
    double strength=strengthMultiplier(target,null);
    return EnergyScaling.multiplier(difference(target, null))*strength;
  }

  /** Covers direct and reflective setter calls, including writes that skip setHealth. */
  public static Object dataHealth(Object item, Object requested) {
    if (!(requested instanceof Float value)
        || !(item instanceof SynchedEntityData.DataItem<?> data) || data.getAccessor() != healthKey) return requested;
    State state = ITEMS.get(item);
    if (state == null || state.busy) return requested;
    LivingEntity target = state.owner.get();
    if (target == null) return requested;
    state.busy = true;
    try {
      float baseline = state.observed;
      if (consume(target, baseline, value)) baseline = value;
      float result = adjust(target, baseline, value);
      state.observed = result;
      return result;
    } finally {
      state.busy = false;
    }
  }

  /** Reconcile an unobserved raw field write at the next standard read and mark it for sync. */
  public static Object readData(Object item, Object raw) {
    if (!(raw instanceof Float value)
        || !(item instanceof SynchedEntityData.DataItem<?> data) || data.getAccessor() != healthKey) return raw;
    State state = ITEMS.get(item);
    if (state == null
        || state.busy
        || Float.floatToIntBits(value) == Float.floatToIntBits(state.observed)) return raw;
    LivingEntity target = state.owner.get();
    if (target == null) return raw;
    state.busy = true;
    try {
      float baseline = state.observed;
      if (consume(target, baseline, value)) baseline = value;
      float result = adjust(target, baseline, value);
      state.observed = result;
      if (Float.floatToIntBits(result) != Float.floatToIntBits(value))
        commit(target, state, result);
      return result;
    } finally {
      state.busy = false;
    }
  }

  private static boolean consume(LivingEntity target, float before, float value) {
    var stack = WRITES.get();
    Write write = stack == null ? null : stack.peek();
    if (write == null || write.target.get() != target || write.consumed || write.healing) return false;
    write.consumed = true;
    if (write.resolved) return CommitPermit.consume(target, before, value);
    return write.tracked && Float.floatToIntBits(write.before) == Float.floatToIntBits(before)
        && Float.floatToIntBits(write.expected) == Float.floatToIntBits(value);
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
