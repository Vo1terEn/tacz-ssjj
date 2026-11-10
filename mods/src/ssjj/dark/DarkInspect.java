package ssjj.dark;

import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;

/** Repairs a cold native inspect input without substituting an animation player. */
final class DarkInspect {
    static void prepare(LuaAnimationStateMachine<GunAnimationStateContext> machine,
                        GunAnimationStateContext freshContext, boolean allowed) {
        if (machine == null) return;
        if (!machine.isInitialized() && allowed) {
            machine.setContext(freshContext);
            machine.initialize();
            machine.trigger("draw");
        }
        if (machine.isInitialized()) machine.trigger(allowed ? "dark_inspect_ready" : "dark_inspect_blocked");
    }

    static boolean request(LuaAnimationStateMachine<GunAnimationStateContext> machine,
                           GunAnimationStateContext freshContext, boolean allowed) {
        prepare(machine, freshContext, allowed);
        if (!allowed || machine == null || !machine.isInitialized() || machine.getContext() == null) return false;
        var context = machine.getContext();
        if (!context.hasAnimationPrototype("inspect")) return false;
        var running = machine.getAnimationController().getAnimation(context.getTrack(0, 1));
        // The native handler runs first. Its blend target is already an accepted
        // input, so the fallback must not restart it or play a second copy.
        if (isInspect(running)) return false;
        machine.trigger("inspect");
        return isInspect(machine.getAnimationController().getAnimation(context.getTrack(0, 1)));
    }

    private static boolean isInspect(ObjectAnimationRunner runner) {
        if (runner == null) return false;
        if (runner.getTransitionTo() != null && isInspect(runner.getTransitionTo())) return true;
        return "inspect".equals(runner.getAnimation().name) && !runner.isStopped();
    }

    private DarkInspect() {}
}
