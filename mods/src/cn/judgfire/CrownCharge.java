package cn.judgfire;

/** Per-weapon held time. No wall clock catch-up or attacks after switching away. */
public final class CrownCharge {
  public static final int DECAY_INTERVAL = 120;
  private double charge;
  private int decayTicks, attackTicks;
  private boolean locked;

  public CrownCharge(double charge) {
    this.charge = clamp(charge);
  }

  public CrownCharge(double charge, int decayTicks) {
    this(charge);
    this.decayTicks = Math.floorMod(decayTicks, DECAY_INTERVAL);
  }

  public static double clamp(double value) {
    return Double.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
  }

  public double value() {
    return charge;
  }

  public void hit() {
    if (locked) return;
    charge = clamp(charge + 4);
    if (charge >= 100) decay();
  }

  public void lock(boolean active) {
    if (active) {
      locked = true;
      charge = 100;
      decayTicks = 0;
    } else if (locked) {
      locked = false;
      decay();
    }
  }

  private void decay() {
    charge *= .75;
    decayTicks = 0;
  }

  public int tier(double mid, double high) {
    return charge >= Math.max(mid, high) ? 2 : charge >= Math.min(mid, high) ? 1 : 0;
  }

  public int interval(double mid, double high) {
    return switch (tier(mid, high)) {
      case 2 -> 4;
      case 1 -> 20;
      default -> 60;
    };
  }

  public boolean tick(double mid, double high) {
    // Normalize a full charge saved by an older version once, without a second
    // timer decay on the same tick. Ordinary hits normalize immediately above.
    if (locked) {
      charge = 100;
      decayTicks = 0;
    } else if (charge >= 100) decay();
    else if (++decayTicks >= DECAY_INTERVAL) decay();
    return ++attackTicks >= interval(mid, high);
  }

  public void attacked() {
    attackTicks = 0;
  }

  public int decayProgress() {
    return decayTicks;
  }

  public void pause() {
    attackTicks = 0;
  }

  public static boolean inRange(double x, double y, double z) {
    return CombatConfig.inRange(x, y, z);
  }
}
