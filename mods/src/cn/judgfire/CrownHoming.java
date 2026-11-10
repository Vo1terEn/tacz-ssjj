package cn.judgfire;

import net.minecraft.world.phys.Vec3;

/** Cosmetic world-space flight only. Server damage is immediate, before the animation. */
public final class CrownHoming {
  public static boolean accepts(boolean hostile, boolean marked) {
    return hostile || marked;
  }

  public static double duration(Vec3 start, Vec3 end) {
    return Math.max(.16, Math.min(.6, start.distanceTo(end) / 75));
  }

  public static Vec3 point(Vec3 start, Vec3 end, double progress) {
    double t = Math.max(0, Math.min(1, progress)), u = 1 - t;
    Vec3 delta = end.subtract(start);
    Vec3 side = delta.cross(new Vec3(0, 1, 0)).normalize();
    double flare = Math.min(1, delta.length() / 3);
    Vec3 a = start.add(delta.scale(.25)).add(side.scale(.65 * flare)).add(0, .7 * flare, 0);
    Vec3 b = start.add(delta.scale(.76));
    return start
        .scale(u * u * u)
        .add(a.scale(3 * u * u * t))
        .add(b.scale(3 * u * t * t))
        .add(end.scale(t * t * t));
  }
}
