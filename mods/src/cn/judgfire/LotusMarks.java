package cn.judgfire;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owner-specific marks. Server game ticks, refresh rather than stack. */
public final class LotusMarks {
  private final Map<UUID, Map<UUID, Long>> marks = new HashMap<>();

  public void refresh(UUID owner, UUID enemy, long now, int duration) {
    if (owner.equals(enemy) || duration <= 0) return;
    marks.computeIfAbsent(owner, key -> new HashMap<>()).put(enemy, now + duration);
  }

  public boolean active(UUID owner, UUID enemy, long now) {
    var targets = marks.get(owner);
    if (targets == null) return false;
    Long end = targets.get(enemy);
    return end != null && end > now;
  }

  public static float damage(float amount, double reduction, boolean active) {
    return active ? amount * (float) (1 - Math.max(0, Math.min(1, reduction))) : amount;
  }

  public void prune(long now) {
    marks.values().forEach(targets -> targets.values().removeIf(end -> end <= now));
    marks.values().removeIf(Map::isEmpty);
  }

  public void removeOwner(UUID owner) {
    marks.remove(owner);
  }

  public int size() {
    return marks.values().stream().mapToInt(Map::size).sum();
  }
}
