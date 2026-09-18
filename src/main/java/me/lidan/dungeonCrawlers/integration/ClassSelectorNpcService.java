package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Location;

import java.util.UUID;

/** Optional class-selector NPC boundary; core run state never depends on Citizens. */
public interface ClassSelectorNpcService {
    boolean available();

    void createFor(UUID instanceId, Location location);

    void removeFor(UUID instanceId);

    void shutdown();
}
