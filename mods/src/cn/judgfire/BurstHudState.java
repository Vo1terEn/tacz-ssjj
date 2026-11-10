package cn.judgfire;

/**
 * Presentation-only countdown, corrected by authoritative BurstState packets. No activation
 * prediction, damage or cooldown authority lives in this class.
 */
public final class BurstHudState {
  private int remaining, total = 1;
  private boolean known;

  public void reset() {
    remaining = 0;
    total = 1;
    known = false;
  }

  public void sync(int ticks, int configuredTotal, boolean started) {
    remaining = Math.max(0, ticks);
    total = Math.max(1, Math.max(remaining, configuredTotal));
    if (started && remaining > 0) total = remaining;
    known = true;
  }

  public void tick() {
    if (remaining > 0) remaining--;
  }

  public boolean known() {
    return known;
  }

  public int remaining() {
    return remaining;
  }

  public boolean ready(boolean loaded, boolean blocked) {
    return known && remaining == 0 && loaded && !blocked;
  }

  public float progress(float partialTick) {
    if (!known) return 0;
    float rest = Math.max(0, remaining - Math.max(0, Math.min(1, partialTick)));
    return Math.max(0, Math.min(1, 1 - rest / total));
  }

  public record Layout(float x, float y, float scale) {}

  public static Layout layout(int width, int height) {
    float scale = Math.min(.5f, Math.max(.15f, (width - 129f) / 210f));
    // Native TaCZ weapon icon starts at width-117. Leave eight GUI units to
    // its left and keep the card above the centered vanilla hotbar.
    return new Layout(
        Math.max(4, width - 125 - 210 * scale), Math.max(4, height - 24 - 90 * scale), scale);
  }
}
