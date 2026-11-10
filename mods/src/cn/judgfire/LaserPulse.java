package cn.judgfire;

/** Time envelope independent of rendering; full reach survives low frame rates. */
public final class LaserPulse {
  private LaserPulse() {}

  private static double smooth(double x) {
    x = Math.max(0, Math.min(1, x));
    return x * x * (3 - 2 * x);
  }

  public static double length(double seconds) {
    if (seconds < 0 || seconds >= .2) return 0;
    if (seconds < .045) return .033 + .967 * smooth(seconds / .045);
    if (seconds <= .125) return 1;
    return .033 + .967 * smooth((.2 - seconds) / .075);
  }

  public static float width(double seconds) {
    double envelope = seconds < .0667 ? smooth(seconds / .0667) : smooth((.2 - seconds) / .1333);
    return (float) (.75 + .85 * envelope);
  }

  public static float alpha(double seconds) {
    return seconds < 0 ? 0 : (float) Math.min(1, Math.max(0, (.2 - seconds) / .06));
  }
}
