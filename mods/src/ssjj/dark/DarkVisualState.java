package ssjj.dark;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 只存表现同步数据；不引用客户端渲染类，专用服务器也可安全加载消息注册。 */
public final class DarkVisualState {
    public record Cast(int entityId, UUID playerId, long startTick, int chargeTicks, long activeUntil, float yaw, boolean stopped) {
        public boolean ringActive(long now, float partial) {
            return !stopped && now >= startTick && (now - startTick) + partial < activeUntil - startTick;
        }
        public long expiresAt() { return Math.max(activeUntil, startTick + chargeTicks + 24); }
    }

    /** 原buff外圈的2秒循环：起始段保留，之后回读已进入稳定发射的2～4秒。 */
    static float ringClock(float clock) { return clock < 4 ? clock : 2 + (clock - 4) % 2; }
    public static final Map<UUID, Cast> CASTS = new HashMap<>();

    public static void accept(Cast cast) {
        Cast old = CASTS.get(cast.playerId());
        if (old != null && old.startTick() > cast.startTick()) return;
        if (cast.stopped()) CASTS.remove(cast.playerId());
        else CASTS.put(cast.playerId(), cast);
    }

    private DarkVisualState() {}
}
