package cn.judgfire;

import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server hit tolerance, independent of the visual beam width. */
public final class LaserCollision {
  public static final float DAMAGE = 16f;
  public static final double RADIUS = .35;

  public static Optional<Vec3> contact(AABB target, Vec3 start, Vec3 end) {
    AABB expanded = target.inflate(RADIUS);
    return expanded.contains(start) ? Optional.of(start) : expanded.clip(start, end);
  }

  public static Vec3 onBody(AABB box, Vec3 p) {
    return new Vec3(
        Math.max(box.minX, Math.min(box.maxX, p.x)),
        Math.max(box.minY, Math.min(box.maxY, p.y)),
        Math.max(box.minZ, Math.min(box.maxZ, p.z)));
  }
}
