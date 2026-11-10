package cn.judgfire;

/** Boundaries use server game ticks, never client clocks. */
public final class BurstTiming {
  public static final int DURATION = 300;

  public static boolean active(long now, long end) {
    return end > now;
  }

  public static boolean canStun(boolean alreadyStunned, long now, long immuneUntil) {
    return !alreadyStunned && now >= immuneUntil;
  }

  public static int remaining(long now, long end) {
    return (int) Math.max(0, Math.min(Integer.MAX_VALUE, end - now));
  }

  public static float weaponDamage(float base, boolean active) {
    return active ? CombatConfig.burstWeaponDamage() : base;
  }

  public static float crownDamage(float base, boolean active) {
    return active ? CombatConfig.burstCrownDamage() : base;
  }
}
