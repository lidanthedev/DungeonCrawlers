package me.lidan.dungeonCrawlers.core.encounter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Decides when a player has hovered above a ground boss long enough to be pulled back down. */
public final class PerchGuard {
    public static final double HEIGHT = 5;
    public static final long GRACE_MILLIS = 3000;
    public static final long REPEAT_MILLIS = 1000;
    private final Map<UUID, Long> since = new HashMap<>();

    /** True when the player must be pulled now; repeats every second while they stay perched. */
    public boolean pull(UUID player, double heightAboveBoss, long now) {
        if (heightAboveBoss < HEIGHT) { since.remove(player); return false; }
        long start = since.computeIfAbsent(player, id -> now);
        if (now - start < GRACE_MILLIS) return false;
        since.put(player, now - GRACE_MILLIS + REPEAT_MILLIS);
        return true;
    }

    public void clear() { since.clear(); }
}
