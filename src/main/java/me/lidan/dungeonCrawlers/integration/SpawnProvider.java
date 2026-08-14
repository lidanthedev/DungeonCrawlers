package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Location;

import java.util.Optional;

public interface SpawnProvider {
    Optional<Location> spawn();

    String source();
}

