package ssjj.dark;

/** Standalone rules read from the three supplied weapon JSON files. No engine state. */
final class DarkMeleeRules {
    record Strike(float factor, double knockback, int cooldown, int delay,
                  double range, double aperture) {}

    static final Strike LIGHT_A = new Strike(1, .2, 8, 3, 3, 90);
    static final Strike LIGHT_B = new Strike(1, .2, 8, 3, 3, 90);
    static final Strike HEAVY = new Strike(1.65F, .2, 20, 10, 3.2, 45);
    static final int MAX_TARGETS = 32;
    static final int COMBO_TIMEOUT = 40;

    record Scheduled(Strike strike, int combo, boolean heavy, long hitAt, long readyAt) {}

    /** The same clock used by the server and the standalone Java probe. */
    static final class Timeline {
        private long readyAt, comboUntil, lastAccepted = Long.MIN_VALUE;
        private int nextLight;
        private Scheduled pending;

        Scheduled begin(boolean heavy, long tick) {
            if (tick < 0 || tick > Long.MAX_VALUE - COMBO_TIMEOUT || pending != null
                || tick < readyAt || tick == lastAccepted) return null;
            if (tick > comboUntil) nextLight = 0;
            int combo = heavy ? -1 : nextLight;
            Strike strike = heavy ? HEAVY : combo == 0 ? LIGHT_A : LIGHT_B;
            nextLight = heavy ? 0 : 1 - nextLight;
            comboUntil = tick + COMBO_TIMEOUT;
            readyAt = tick + strike.cooldown();
            lastAccepted = tick;
            pending = new Scheduled(strike, combo, heavy, tick + strike.delay(), readyAt);
            return pending;
        }

        Scheduled poll(long tick) {
            if (pending == null || tick < pending.hitAt()) return null;
            Scheduled due = pending;
            pending = null;
            return due;
        }

        void cancelPending() { pending = null; nextLight = 0; comboUntil = 0; }
        boolean hasPending() { return pending != null; }
        int remaining(long tick) { return (int) Math.max(0, Math.min(20, readyAt - tick)); }
    }

    static String canonical(String raw) {
        if (raw == null) return "";
        return switch (raw) {
            case "ssjj_dark:darkdiamond", "ssjj_dark:darkdiamond_mix",
                 "ssjj_dark:darkdiamond_nightguardian", "ssjj_dark:darkdiamond_pure" -> "ssjj_dark:darkdiamond";
            case "ssjj_dark:darkdiamond1", "ssjj_dark:darkdiamond1_mix",
                 "ssjj_dark:darkdiamond1_nightguardian", "ssjj_dark:darkdiamond1_pure" -> "ssjj_dark:darkdiamond1";
            case "ssjj_dark:darkdiamond2", "ssjj_dark:darkdiamond2_mix",
                 "ssjj_dark:darkdiamond2_nightguardian", "ssjj_dark:darkdiamond2_pure" -> "ssjj_dark:darkdiamond2";
            default -> "";
        };
    }

    static int grade(String canonical) {
        return switch (canonical) {
            case "ssjj_dark:darkdiamond" -> 0;
            case "ssjj_dark:darkdiamond1" -> 1;
            case "ssjj_dark:darkdiamond2" -> 2;
            default -> -1;
        };
    }

    static float damage(String canonical, Strike strike) {
        int grade = grade(canonical);
        return grade < 0 ? 0 : (7 + grade) * strike.factor();
    }

    /** Angle is the full cone aperture; distance is measured to the target bounds. */
    static boolean insideCone(double dx, double dy, double dz, double lx, double ly,
                              double lz, double range, double aperture) {
        double distance2 = dx * dx + dy * dy + dz * dz;
        double look2 = lx * lx + ly * ly + lz * lz;
        if (!Double.isFinite(distance2) || !Double.isFinite(look2)
            || !Double.isFinite(range) || !Double.isFinite(aperture)
            || range <= 0 || aperture <= 0 || aperture > 180 || look2 < 1.0E-12
            || distance2 > range * range + 1.0E-7) return false;
        if (distance2 < 1.0E-12) return true;
        double dot = (dx * lx + dy * ly + dz * lz) / Math.sqrt(distance2 * look2);
        return dot + 1.0E-7 >= Math.cos(Math.toRadians(aperture * .5));
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private DarkMeleeRules() {}
}
