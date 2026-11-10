package cn.ssjjparasite2.client;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Finite-path cylinder geometry and collision-safe attraction to its exact center axis. */
public final class ParasiteShockwaveRules {
    public static final double RANGE = 64, RADIUS = 1, ENHANCED_RADIUS = 3;
    public static final double MAX_RANGE = 256, MIN_RADIUS = .1, MAX_RADIUS = 16;
    private ParasiteShockwaveRules() {}

    /** Fixed protocol limits; never compare a server pulse against client-local configuration. */
    public static boolean validRadius(double radius) {
        return Double.isFinite(radius) && radius >= MIN_RADIUS && radius <= MAX_RADIUS;
    }

    public static boolean replace(boolean server, boolean canceled, boolean tierTwo, boolean cannon, boolean owner) {
        return server && !canceled && tierTwo && cannon && owner;
    }

    public static boolean enemy(boolean alive, boolean spectator, boolean attackable, boolean self,
                                boolean allied, boolean creative, boolean pvpAllowed, boolean sameVehicle) {
        return alive && !spectator && attackable && !self && !allied && !creative && pvpAllowed && !sameVehicle;
    }

    public static Vec3 axis(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 d = end.subtract(start);
        double length = d.lengthSqr();
        double t = length < 1e-12 ? 0 : Math.max(0, Math.min(1, point.subtract(start).dot(d) / length));
        return start.add(d.scale(t));
    }

    private static Vec3 onBody(AABB box, Vec3 point) {
        return new Vec3(Math.max(box.minX, Math.min(box.maxX, point.x)),
            Math.max(box.minY, Math.min(box.maxY, point.y)), Math.max(box.minZ, Math.min(box.maxZ, point.z)));
    }

    public static Optional<Vec3> contact(AABB box, Vec3 start, Vec3 end) {
        return contact(box, start, end, RADIUS);
    }

    public static Optional<Vec3> contact(AABB box, Vec3 start, Vec3 end, double radius) {
        if (!validRadius(radius)) return Optional.empty();
        Vec3 d = end.subtract(start);
        double length = d.length();
        if (length < 1e-8) return Optional.empty();
        Vec3 unit = d.scale(1 / length), half = new Vec3(box.getXsize(), box.getYsize(), box.getZsize()).scale(.5);
        double center = box.getCenter().subtract(start).dot(unit);
        double extent = Math.abs(unit.x) * half.x + Math.abs(unit.y) * half.y + Math.abs(unit.z) * half.z;
        if (center + extent < 0 || center - extent > length) return Optional.empty();
        // Distance squared from a point on the segment to a box is convex. This avoids
        // the square cross-section false positives of merely inflating an AABB.
        double lo = 0, hi = 1;
        for (int i = 0; i < 48; i++) {
            double a = (2 * lo + hi) / 3, b = (lo + 2 * hi) / 3;
            Vec3 pa = start.add(d.scale(a)), pb = start.add(d.scale(b));
            if (pa.distanceToSqr(onBody(box, pa)) < pb.distanceToSqr(onBody(box, pb))) hi = b;
            else lo = a;
        }
        Vec3 point = start.add(d.scale((lo + hi) * .5)), body = onBody(box, point);
        return point.distanceToSqr(body) <= radius * radius + 1e-6 ? Optional.of(body) : Optional.empty();
    }

    /** Only the destination is tested. Side walls do not block suction to the center axis.
     * If the closest point is occupied, search along that same axis, never around it.
     */
    public static Optional<Vec3> destination(AABB body, Vec3 feet, Vec3 start, Vec3 end, Predicate<AABB> safe) {
        Vec3 center = body.getCenter(), nearest = axis(center, start, end), delta = end.subtract(start);
        double length = delta.length();
        if (length < 1e-8 || center.distanceToSqr(nearest) < 1e-8) return Optional.empty();
        Vec3 direction = delta.scale(1 / length), offset = center.subtract(feet);
        double projected = nearest.subtract(start).dot(direction);
        Vec3 position = nearest.subtract(offset);
        if (safe.test(body.move(position.subtract(feet)))) return Optional.of(position);
        // Half-block samples bound collision probes; also test the exact clipped endpoints.
        // Every candidate places the complete body's center on the finite visual/server axis.
        boolean checkedStart = projected <= 0, checkedEnd = projected >= length;
        for (double step = .5; step < length + .5 && !(checkedStart && checkedEnd); step += .5) {
            for (int sign : new int[]{-1, 1}) {
                double distance = Math.max(0, Math.min(length, projected + sign * step));
                if (distance == 0) { if (checkedStart) continue; checkedStart = true; }
                if (distance == length) { if (checkedEnd) continue; checkedEnd = true; }
                position = start.add(direction.scale(distance)).subtract(offset);
                if (safe.test(body.move(position.subtract(feet)))) return Optional.of(position);
            }
        }
        return Optional.empty();
    }
}
