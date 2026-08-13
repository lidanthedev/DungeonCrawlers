package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface MythicMobGateway {
    boolean isConfigured(String mobId);

    SpawnResult spawn(String mobId, Location location, double level);

    boolean isMythicMob(Entity entity);

    boolean remove(Entity entity);

    record SpawnResult(boolean successful, Entity entity, String detail) {}
}

