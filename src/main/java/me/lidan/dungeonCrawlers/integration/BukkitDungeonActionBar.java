package me.lidan.dungeonCrawlers.integration;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Uses the verified CaveCrawlers action bar and falls back to Paper's player API. */
public final class BukkitDungeonActionBar implements DungeonActionBar {
    private final DungeonActionBar primary;

    public BukkitDungeonActionBar(DungeonActionBar primary) {
        this.primary = Objects.requireNonNull(primary, "primary");
    }

    @Override
    public void show(Player player, Component message) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(message, "message");
        try {
            primary.show(player, message);
        } catch (RuntimeException exception) {
            player.sendActionBar(message);
        }
    }
}
