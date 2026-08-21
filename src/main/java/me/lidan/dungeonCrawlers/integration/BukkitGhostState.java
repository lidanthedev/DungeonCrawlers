package me.lidan.dungeonCrawlers.integration;

import org.bukkit.GameMode;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Applies the server-side presentation and protections for a ghost player. */
public final class BukkitGhostState {
    private BukkitGhostState() { }

    public static void enter(Player player) {
        apply(player);
        clearMobTargets(player);
    }

    public static void refresh(Player player) {
        apply(player);
    }

    private static void apply(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvisible(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
    }

    public static void exit(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
    }

    public static void clearMobTargets(Player player) {
        player.getWorld().getEntities().stream()
                .filter(Mob.class::isInstance)
                .map(Mob.class::cast)
                .filter(mob -> player.equals(mob.getTarget()))
                .forEach(mob -> mob.setTarget(null));
    }
}
