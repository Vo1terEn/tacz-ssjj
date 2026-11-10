package cn.ssjjparasite2.client;

/** Tick-based target marks, independent of effects, attributes and saved entity NBT. */
public final class ParasiteVisceralRules {
    public static final int MAX_STACKS = 5, DURATION_TICKS = 200;
    public static final float BONUS_PER_STACK = .5f;
    public record Mark(int stacks, long until) {}
    private ParasiteVisceralRules() {}

    public static int stacks(Mark mark, long now) {
        return mark == null || now >= mark.until() ? 0 : Math.max(0, Math.min(MAX_STACKS, mark.stacks()));
    }

    public static Mark hit(Mark mark, long now) {
        return new Mark(Math.min(MAX_STACKS, stacks(mark, now) + 1), now + DURATION_TICKS);
    }

    public static float damage(float amount, int stacks) {
        return (float) Math.min(Float.MAX_VALUE,
            (double) amount * (1 + BONUS_PER_STACK * Math.max(0, Math.min(MAX_STACKS, stacks))));
    }
}
