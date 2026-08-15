package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Captures/restores location and basic attributes only; inventory and potion effects are untouched. */
public final class BukkitPlayerRecovery {
    private BukkitPlayerRecovery() { }

    public static PlayerRecoverySnapshot capture(Player player, UUID instanceId, Clock clock) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(clock, "clock");
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        World world = Objects.requireNonNull(location.getWorld(), "player world");
        int maximumAir = Math.max(1, player.getMaximumAir());
        int remainingAir = Math.clamp(player.getRemainingAir(), 0, maximumAir);
        double health = Math.max(0D, player.getHealth());
        int foodLevel = Math.clamp(player.getFoodLevel(), 0, 20);
        float saturation = Math.max(0F, player.getSaturation());
        float exhaustion = Math.max(0F, player.getExhaustion());
        return new PlayerRecoverySnapshot(player.getUniqueId(), instanceId, world.getName(), location.getX(),
                location.getY(), location.getZ(), location.getYaw(), location.getPitch(),
                player.getGameMode().name(), health, foodLevel, saturation, exhaustion,
                Math.max(0, player.getFireTicks()), remainingAir, maximumAir,
                clock.instant());
    }

    public static RestoreResult restore(Player player, PlayerRecoverySnapshot snapshot, Server server,
                                        SpawnProvider fallback) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(fallback, "fallback");
        if (!player.getUniqueId().equals(snapshot.playerId())) {
            return RestoreResult.failure("snapshot belongs to another player");
        }
        GameMode gameMode;
        try { gameMode = GameMode.valueOf(snapshot.gameMode()); }
        catch (IllegalArgumentException exception) { return RestoreResult.failure("invalid snapshot game mode"); }
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return RestoreResult.failure("MAX_HEALTH attribute unavailable");

        Location exact = Optional.ofNullable(server.getWorld(snapshot.world()))
                .map(world -> new Location(world, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch()))
                .orElse(null);
        boolean exactRestore = exact != null && player.teleport(exact);
        if (!exactRestore) {
            Location spawn = fallback.spawn().map(Location::clone).orElse(null);
            if (spawn == null || spawn.getWorld() == null || !player.teleport(spawn)) {
                return RestoreResult.failure("exact location unavailable and fallback spawn failed");
            }
        }
        return restoreAttributes(player, snapshot, exactRestore, maxHealth.getValue());
    }

    public static RestoreResult restore(Player player, PlayerRecoverySnapshot snapshot, Server server,
                                        SpawnProvider fallback, double maximumHealth) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(fallback, "fallback");
        if (!player.getUniqueId().equals(snapshot.playerId())) {
            return RestoreResult.failure("snapshot belongs to another player");
        }
        GameMode gameMode;
        try { gameMode = GameMode.valueOf(snapshot.gameMode()); }
        catch (IllegalArgumentException exception) { return RestoreResult.failure("invalid snapshot game mode"); }
        Location exact = Optional.ofNullable(server.getWorld(snapshot.world()))
                .map(world -> new Location(world, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch()))
                .orElse(null);
        boolean exactRestore = exact != null && player.teleport(exact);
        if (!exactRestore) {
            Location spawn = fallback.spawn().map(Location::clone).orElse(null);
            if (spawn == null || spawn.getWorld() == null || !player.teleport(spawn)) {
                return RestoreResult.failure("exact location unavailable and fallback spawn failed");
            }
        }
        return restoreAttributes(player, snapshot, exactRestore, maximumHealth, gameMode);
    }

    private static RestoreResult restoreAttributes(Player player, PlayerRecoverySnapshot snapshot,
                                                   boolean exactRestore, double maximumHealth) {
        GameMode gameMode;
        try { gameMode = GameMode.valueOf(snapshot.gameMode()); }
        catch (IllegalArgumentException exception) { return RestoreResult.failure("invalid snapshot game mode"); }
        return restoreAttributes(player, snapshot, exactRestore, maximumHealth, gameMode);
    }

    private static RestoreResult restoreAttributes(Player player, PlayerRecoverySnapshot snapshot,
                                                   boolean exactRestore, double maximumHealth, GameMode gameMode) {
        try {
            player.setGameMode(gameMode);
            if (!Double.isFinite(maximumHealth) || maximumHealth <= 0) {
                return RestoreResult.failure("invalid MAX_HEALTH attribute");
            }
            player.setHealth(Math.max(0.5, Math.min(snapshot.health(), maximumHealth)));
            player.setFoodLevel(snapshot.foodLevel());
            player.setSaturation(snapshot.saturation());
            player.setExhaustion(snapshot.exhaustion());
            player.setFireTicks(snapshot.fireTicks());
            player.setMaximumAir(snapshot.maximumAir());
            player.setRemainingAir(snapshot.remainingAir());
        } catch (RuntimeException exception) {
            return RestoreResult.failure("player attributes could not be restored: " + exception.getMessage());
        }
        return new RestoreResult(true, exactRestore ? RestoreSource.EXACT : RestoreSource.FALLBACK,
                exactRestore ? "exact snapshot location restored" : "fallback spawn restored");
    }

    public enum RestoreSource { EXACT, FALLBACK }

    public record RestoreResult(boolean successful, RestoreSource source, String detail) {
        public RestoreResult {
            Objects.requireNonNull(detail);
            if (successful && source == null) throw new IllegalArgumentException("successful restore needs source");
            if (!successful && source != null) throw new IllegalArgumentException("failed restore has no source");
        }

        private static RestoreResult failure(String detail) { return new RestoreResult(false, null, detail); }
    }
}
