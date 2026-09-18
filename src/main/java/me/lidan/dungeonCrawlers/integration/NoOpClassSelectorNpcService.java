package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Location;

import java.util.UUID;

/** Keeps all class selection functionality working when Citizens is unavailable. */
public final class NoOpClassSelectorNpcService implements ClassSelectorNpcService {
    @Override
    public boolean available() { return false; }

    @Override
    public void createFor(UUID instanceId, Location location) { }

    @Override
    public void removeFor(UUID instanceId) { }

    @Override
    public void shutdown() { }
}
