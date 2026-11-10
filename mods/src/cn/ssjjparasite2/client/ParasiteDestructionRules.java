package cn.ssjjparasite2.client;

import java.util.ArrayList;
import java.util.List;

/** Five successful, already settled cannon impacts form one independent detonation. */
public final class ParasiteDestructionRules {
    /** Ordinary impact losses only. An explosion is never inserted in this history. */
    public record Stack(List<Double> losses) {
        public Stack {
            losses = List.copyOf(losses);
            if (losses.size() > 5 || losses.stream().anyMatch(n -> !(n > 0) || !Double.isFinite(n)))
                throw new IllegalArgumentException("Invalid ordinary-hit history");
        }
        public int count() { return losses.size(); }
        public double damage() {
            double total = 0;
            for (double loss : losses) total = total > Double.MAX_VALUE - loss ? Double.MAX_VALUE : total + loss;
            return total;
        }
    }
    private ParasiteDestructionRules() {}

    public static double loss(double healthBefore, double absorptionBefore, double healthAfter, double absorptionAfter) {
        if (!Double.isFinite(healthBefore) || !Double.isFinite(absorptionBefore)
            || !Double.isFinite(healthAfter) || !Double.isFinite(absorptionAfter)) return 0;
        double amount = (healthBefore - Math.max(0, healthAfter))
            + (absorptionBefore - Math.max(0, absorptionAfter));
        return Double.isFinite(amount) ? Math.max(0, amount) : 0;
    }

    public static Stack hit(Stack previous, double loss) {
        if (!(loss > 0) || !Double.isFinite(loss)) return previous;
        var history = new ArrayList<Double>();
        if (previous != null && previous.count() < 5) history.addAll(previous.losses);
        history.add(loss);
        return new Stack(history);
    }

    public static boolean retain(boolean stock, float roll) {
        return retain(stock ? .30f : 0, roll);
    }

    public static boolean retain(float chance, float roll) {
        return Float.isFinite(chance) && chance > 0 && chance <= 1
            && Float.isFinite(roll) && roll >= 0 && roll < 1 && roll < chance;
    }

    public static Stack retained(Stack completed) {
        if (completed == null || completed.count() != 5) throw new IllegalArgumentException("Incomplete detonation");
        return new Stack(completed.losses.subList(1, 5));
    }

    public static float nativeAmount(double amount) {
        return Double.isFinite(amount) && amount > 0 ? (float) Math.min(Float.MAX_VALUE, amount) : 0;
    }
}
