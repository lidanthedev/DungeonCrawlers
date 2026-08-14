package me.lidan.dungeonCrawlers.config.registry;

import java.util.LinkedHashSet;
import java.util.Set;

public final class EncounterRegistry {
    private final Set<String> ids = new LinkedHashSet<>(Set.of("basic"));

    public synchronized boolean register(String id) {
        if (id == null || !id.matches("^[a-z0-9][a-z0-9_-]{0,63}$")) {
            throw new IllegalArgumentException("invalid encounter id");
        }
        return ids.add(id);
    }

    public synchronized boolean contains(String id) { return ids.contains(id); }
    public synchronized Set<String> snapshot() { return Set.copyOf(ids); }
}
