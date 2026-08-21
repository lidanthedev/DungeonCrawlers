package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Bukkit boundary for lethal damage, ghost restrictions, and reconnect timestamps. */
public final class BukkitDungeonLifecycleListener implements Listener {
    private final PlayerLifecycleService lifecycle;
    private final RunPreparationService runs;
    private final Plugin plugin;
    private final Clock clock;
    private final java.util.function.Consumer<Player> recoveryOnJoin;
    private final Consumer<Player> leaveHandler;

    public BukkitDungeonLifecycleListener(PlayerLifecycleService lifecycle, RunPreparationService runs,
                                          Plugin plugin, Clock clock) {
        this(lifecycle, runs, plugin, clock, ignored -> { });
    }

    public BukkitDungeonLifecycleListener(PlayerLifecycleService lifecycle, RunPreparationService runs,
                                          Plugin plugin, Clock clock,
                                          java.util.function.Consumer<Player> recoveryOnJoin) {
        this(lifecycle, runs, plugin, clock, recoveryOnJoin, ignored -> { });
    }

    public BukkitDungeonLifecycleListener(PlayerLifecycleService lifecycle, RunPreparationService runs,
                                          Plugin plugin, Clock clock,
                                          java.util.function.Consumer<Player> recoveryOnJoin,
                                          Consumer<Player> leaveHandler) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.recoveryOnJoin = Objects.requireNonNull(recoveryOnJoin, "recoveryOnJoin");
        this.leaveHandler = Objects.requireNonNull(leaveHandler, "leaveHandler");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID instanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (instanceId == null) return;
        var state = lifecycle.player(instanceId, player.getUniqueId()).orElse(null);
        if (state == null) return;
        if (state.state() != PlayerLifecycleService.PlayerState.ALIVE) {
            event.setCancelled(true);
            return;
        }
        if (player.getHealth() - event.getFinalDamage() > 0D) return;
        event.setCancelled(true);
        lifecycle.lethal(instanceId, player.getUniqueId(), clock.instant());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGhostDamage(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker != null && isGhost(attacker)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGhostTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && isGhost(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNaturalDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID instanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (instanceId == null) return;
        var state = lifecycle.player(instanceId, player.getUniqueId()).orElse(null);
        if (state == null || state.state() != PlayerLifecycleService.PlayerState.ALIVE) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);
        lifecycle.lethal(instanceId, player.getUniqueId(), clock.instant());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().trim();
        if (command.length() < 2 || command.charAt(0) != '/') return;
        String label = command.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!label.equals("spawn") && !label.equals("essentials:spawn")) return;
        if (runs.instanceFor(event.getPlayer().getUniqueId()).isEmpty()) return;
        event.setCancelled(true);
        leaveHandler.accept(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID instanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (instanceId == null) return;
        var state = lifecycle.player(instanceId, player.getUniqueId()).orElse(null);
        if (state == null || state.state() != PlayerLifecycleService.PlayerState.GHOST) return;
        scheduleGhostEnter(instanceId, player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGhostInteract(PlayerInteractEvent event) {
        if (isGhost(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGhostBreak(BlockBreakEvent event) {
        if (isGhost(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGhostPlace(BlockPlaceEvent event) {
        if (isGhost(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        runs.instanceFor(event.getPlayer().getUniqueId())
                .ifPresent(id -> lifecycle.disconnect(id, event.getPlayer().getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        recoveryOnJoin.accept(event.getPlayer());
        runs.instanceFor(event.getPlayer().getUniqueId())
                .ifPresent(id -> {
                    lifecycle.reconnect(id, event.getPlayer().getUniqueId());
                    if (lifecycle.player(id, event.getPlayer().getUniqueId())
                            .map(value -> value.state() == PlayerLifecycleService.PlayerState.GHOST).orElse(false)) {
                        scheduleGhostEnter(id, event.getPlayer());
                    }
                });
    }

    private void scheduleGhostEnter(UUID instanceId, Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && lifecycle.player(instanceId, player.getUniqueId())
                    .map(value -> value.state() == PlayerLifecycleService.PlayerState.GHOST).orElse(false)) {
                BukkitGhostState.enter(player);
            }
        });
    }

    private boolean isGhost(Player player) {
        UUID instanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        return instanceId != null && lifecycle.player(instanceId, player.getUniqueId())
                .map(value -> value.state() == PlayerLifecycleService.PlayerState.GHOST).orElse(false);
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
