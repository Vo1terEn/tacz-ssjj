package cn.judgfire;

import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;

/** A skill track belongs to one binding, not to a reusable gun display. */
final class BurstAnimation {
  private AnimationStateContext context;
  private AnimationStateMachine<?> machine;
  private int track;

  void capture(AnimationStateContext value, int index) {
    context = value;
    machine = value.getStateMachine();
    track = index;
  }

  boolean present() {
    return context != null;
  }

  boolean bound() {
    return context != null
        && machine != null
        && context.getStateMachine() == machine
        && machine.getContext() == context
        && machine.isInitialized();
  }

  void clear() {
    var old = context;
    int oldTrack = track;
    boolean stop = bound();
    // Forget the old binding even when TaCZ already replaced or exited it.
    context = null;
    machine = null;
    track = 0;
    if (stop) old.stopAnimation(oldTrack);
  }
}
