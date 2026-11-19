package cn.ssjjhyper.client;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;

/** Identify native actions by their runners, independent of TaCZ's logical track layout. */
final class HyperNativeActions {
    private HyperNativeActions() {}
    /** A nonzero native blend keeps its old root until controller.update(). */
    private static ObjectAnimationRunner effectiveRunner(ObjectAnimationRunner runner) {
        while (runner != null && runner.getTransitionTo() != null) runner = runner.getTransitionTo();
        return runner;
    }
    static boolean busy(AnimationStateContext context, AnimationController controller) {
        for (int index : context.getTrackArray().getByIndex(0)) {
            var runner = effectiveRunner(controller.getAnimation(index));
            if (runner == null || runner.isStopped()) continue;
            String name = runner.getAnimation().name;
            if (name.equals("draw") || name.equals("put_away") || name.startsWith("reload_")
                || name.equals("inspect") || name.equals("inspect_empty") || name.equals("bolt")
                || name.startsWith("melee_")) return true;
        }
        return false;
    }
    static void replaceDraw(AnimationStateContext context, AnimationController controller) {
        for (int index : context.getTrackArray().getByIndex(0)) {
            var runner = effectiveRunner(controller.getAnimation(index));
            if (runner != null && (runner.getAnimation().name.equals("draw")
                || runner.getAnimation().name.equals("put_away"))) controller.removeAnimation(index);
        }
    }
}
