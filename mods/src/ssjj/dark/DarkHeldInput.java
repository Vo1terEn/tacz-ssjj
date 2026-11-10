package ssjj.dark;

/** Actual client scheduling logic, independent of GLFW and the game. */
final class DarkHeldInput {
    enum Action { NONE, LIGHT, HEAVY }
    private boolean armed;
    private double readyAt;
    private double lastClock = Double.NaN;

    static boolean isDarkWeapon(String id) {
        return "ssjj_dark:darkdiamond".equals(id) || "ssjj_dark:darkdiamond1".equals(id)
            || "ssjj_dark:darkdiamond2".equals(id);
    }

    /** Clear physical hold state; keep the incurred cooldown across menu/switch. */
    void reset() { armed = false; }
    void resetClock() { reset(); readyAt = 0; lastClock = Double.NaN; }

    Action poll(boolean enabled, boolean charging, boolean light, boolean heavy, double tick) {
        if (!Double.isFinite(tick)) { reset(); return Action.NONE; }
        if (Double.isFinite(lastClock) && tick < lastClock) { reset(); readyAt = tick; }
        lastClock = tick;
        if (!enabled) { reset(); return Action.NONE; }
        // A held button from another weapon, GUI or unfocused window must first
        // be released. No synthetic first attack is generated when play resumes.
        if (!armed) {
            if (!light && !heavy) armed = true;
            return Action.NONE;
        }
        if (charging || !light && !heavy || tick < readyAt) return Action.NONE;
        Action action = heavy ? Action.HEAVY : Action.LIGHT;
        readyAt = tick + (heavy ? 20 : 8);
        return action;
    }
}
