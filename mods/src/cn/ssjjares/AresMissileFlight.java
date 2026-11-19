package cn.ssjjares;

import net.minecraft.world.phys.Vec3;

/** Rise clear of the wearer, reach the target column, then descend toward its head. */
final class AresMissileFlight {
    static final double TRANSFER_SPEED = 3.2;
    static final double FALL_SPEED = 0.27;
    private static final double RISE_SPEED = 0.75;
    private static final double DROP_HEIGHT = 10.5;
    private static final double FOLLOW_SPEED = 0.95;
    private int rising = 16;
    private Phase phase = Phase.RISE;

    enum Phase { RISE, TRANSFER, DESCENT }
    record Step(Vec3 point, Phase phase, boolean atHead) {}

    Step advance(Vec3 from, Vec3 head) {
        if (phase == Phase.RISE) {
            if (--rising == 0) phase = Phase.TRANSFER;
            return new Step(from.add(0, RISE_SPEED, 0), Phase.RISE, false);
        }
        if (phase == Phase.TRANSFER) {
            var overhead = head.add(0, DROP_HEIGHT, 0);
            if (from.distanceToSqr(overhead) <= TRANSFER_SPEED * TRANSFER_SPEED) {
                phase = Phase.DESCENT;
                return new Step(overhead, Phase.TRANSFER, false);
            }
            return new Step(approach(from, overhead, TRANSFER_SPEED), Phase.TRANSFER, false);
        }
        double y = Math.max(Math.min(head.y, from.y), from.y - FALL_SPEED);
        var column = new Vec3(head.x, y, head.z);
        var next = approach(new Vec3(from.x, y, from.z), column, FOLLOW_SPEED);
        boolean atHead = next == column && y <= head.y;
        return new Step(next, Phase.DESCENT, atHead);
    }

    private static Vec3 approach(Vec3 from, Vec3 to, double speed) {
        var delta = to.subtract(from);
        double distance = delta.length();
        return distance <= speed ? to : from.add(delta.scale(speed / distance));
    }
}
