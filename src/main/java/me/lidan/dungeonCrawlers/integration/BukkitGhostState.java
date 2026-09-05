package me.lidan.dungeonCrawlers.integration;

import org.bukkit.GameMode;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;

/** Applies the server-side presentation and protections for a ghost player. */
public final class BukkitGhostState {
    private static final double TARGET_CLEAR_RADIUS = 32.0D;

    private BukkitGhostState() { }

    public static void enter(Player player) {
        enter(player, Duration.ofSeconds(60));
    }

    public static void enter(Player player, Duration duration) {
        apply(player, duration);
        clearMobTargets(player);
    }

    public static void refresh(Player player) {
        refresh(player, Duration.ofSeconds(60));
    }

    public static void refresh(Player player, Duration duration) {
        apply(player, duration);
    }

    private static void apply(Player player, Duration duration) {
        player.setGameMode(GameMode.SURVIVAL);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, invisibilityTicks(duration), 0,
                false, false, false), true);
        player.setInvulnerable(true);
        player.setCollidable(false);
    }

    public static void exit(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
    }

    private static int invisibilityTicks(Duration duration) {
        long milliseconds = Math.max(50L, duration.toMillis());
        long ticks = (milliseconds + 49L) / 50L;
        return (int) Math.min(Integer.MAX_VALUE, ticks);
    }

    public static void clearMobTargets(Player player) {
        player.getNearbyEntities(TARGET_CLEAR_RADIUS, TARGET_CLEAR_RADIUS, TARGET_CLEAR_RADIUS).stream()
                .filter(Mob.class::isInstance)
                .map(Mob.class::cast)
                .filter(mob -> player.equals(mob.getTarget()))
                .forEach(mob -> mob.setTarget(null));
    }
}
