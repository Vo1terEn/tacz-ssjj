package cn.judgfire;

import java.util.ArrayDeque;
import java.util.Objects;

/** Internal one-shot exact commit protocol, not authority against arbitrary same-JVM code. */
final class CommitPermit implements AutoCloseable {
  private static final ThreadLocal<ArrayDeque<CommitPermit>> STACK = new ThreadLocal<>();
  private final Object target;
  private final float before, after;
  private final Thread owner = Thread.currentThread();
  private boolean consumed, closed;

  private CommitPermit(Object target, float before, float after) {
    this.target = Objects.requireNonNull(target);
    if (!Float.isFinite(before)
        || !Float.isFinite(after)
        || before < 0
        || after < 0
        || after > before) throw new IllegalArgumentException("Invalid resolved health commit");
    this.before = before;
    this.after = after;
  }

  static CommitPermit open(Object target, float before, float after) {
    var permit = new CommitPermit(target, before, after);
    var stack = STACK.get();
    if (stack == null) STACK.set(stack = new ArrayDeque<>());
    stack.push(permit);
    return permit;
  }

  static boolean consume(Object target, float before, float requested) {
    var stack = STACK.get();
    if (stack == null || stack.isEmpty()) return false;
    var permit = stack.peek();
    if (permit.closed
        || permit.consumed
        || permit.target != target
        || Float.floatToIntBits(permit.before) != Float.floatToIntBits(before)
        || Float.floatToIntBits(permit.after) != Float.floatToIntBits(requested)) return false;
    permit.consumed = true; // Consume before the underlying write/read can invoke callbacks.
    return true;
  }

  public void close() {
    if (closed) return;
    var stack = STACK.get();
    if (Thread.currentThread() != owner || stack == null || stack.peek() != this)
      throw new IllegalStateException("Wrong health commit thread/close order");
    stack.pop();
    closed = true;
    if (stack.isEmpty()) STACK.remove();
  }
}
