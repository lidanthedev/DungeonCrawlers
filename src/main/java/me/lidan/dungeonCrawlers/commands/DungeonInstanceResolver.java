package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/** Resolves an instance argument, including the player-relative {@code this} alias. */
public final class DungeonInstanceResolver {
    private DungeonInstanceResolver() { }

    public static UUID require(CommandSender sender, String value, RunPreparationService runs) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(runs, "runs");
        if (value.equalsIgnoreCase("this")) {
            if (!(sender instanceof Player player)) {
                throw new IllegalArgumentException("'this' requires a player sender");
            }
            return runs.instanceFor(player.getUniqueId())
                    .orElseThrow(() -> new IllegalArgumentException("you are not in an active dungeon instance"));
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("instance id must be a UUID or 'this'");
        }
    }
}
