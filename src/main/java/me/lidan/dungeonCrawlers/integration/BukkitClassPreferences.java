package me.lidan.dungeonCrawlers.integration;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** The preference lives in Minecraft player data and survives plugin/server restarts. */
public final class BukkitClassPreferences {
    private final Plugin plugin;
    private final NamespacedKey key;

    public BukkitClassPreferences(Plugin plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "last_dungeon_class");
    }

    public Optional<String> read(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        return player == null ? Optional.empty()
                : Optional.ofNullable(player.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    public void write(UUID playerId, String classId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) player.getPersistentDataContainer().set(key, PersistentDataType.STRING, classId);
    }
}
