package me.lidan.dungeonCrawlers.integration.spawn;

import me.lidan.dungeonCrawlers.integration.SpawnProvider;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;

public final class BukkitSpawnProvider implements SpawnProvider {
    private final Server server;
    private final String configuredWorld;

    public BukkitSpawnProvider(Server server, String configuredWorld) {
        this.server = Objects.requireNonNull(server, "server");
        this.configuredWorld = configuredWorld;
    }

    @Override
    public Optional<Location> spawn() {
        World world = configuredWorld == null || configuredWorld.isBlank()
                ? server.getWorlds().stream().findFirst().orElse(null)
                : server.getWorld(configuredWorld);
        return Optional.ofNullable(world).map(World::getSpawnLocation);
    }

    @Override
    public String source() {
        return configuredWorld == null || configuredWorld.isBlank()
                ? "Bukkit:first-loaded-world"
                : "Bukkit:" + configuredWorld;
    }
}

