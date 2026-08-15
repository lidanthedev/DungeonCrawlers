package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.protection.WorldProtectionService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.time.Clock;
import java.util.function.Supplier;

/** Bukkit event boundary for geometry, PvP, cross-instance, and teleport protection. */
public final class BukkitWorldProtectionListener implements Listener {
    private final WorldProtectionService policy;
    private final Supplier<List<WorldProtectionService.InstanceRegion>> regions;
    private final TeleportPermitService permits;
    private final Clock clock;
    private final Map<java.util.UUID, LaunchSource> launches = new HashMap<>();

    public BukkitWorldProtectionListener(WorldProtectionService policy,
                                         Supplier<List<WorldProtectionService.InstanceRegion>> regions,
                                         TeleportPermitService permits, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.regions = Objects.requireNonNull(regions, "regions");
        this.permits = Objects.requireNonNull(permits, "permits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!policy.canModifyGeometry(event.getBlock().getWorld().getName(), point(event.getBlock().getLocation()),
                regions.get()).allowed()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!policy.canModifyGeometry(event.getBlock().getWorld().getName(), point(event.getBlock().getLocation()),
                regions.get()).allowed()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!policy.canModifyGeometry(event.getBlock().getWorld().getName(), point(event.getBlock().getLocation()),
                regions.get()).allowed()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!policy.canModifyGeometry(event.getBlock().getWorld().getName(), point(event.getBlock().getLocation()),
                regions.get()).allowed()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!policy.canModifyGeometry(event.getBlock().getWorld().getName(), point(event.getBlock().getLocation()),
                regions.get()).allowed()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.blockList().stream().anyMatch(block -> !policy.canModifyGeometry(block.getWorld().getName(),
                point(block.getLocation()), regions.get()).allowed())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.blockList().stream().anyMatch(block -> !policy.canModifyGeometry(block.getWorld().getName(),
                point(block.getLocation()), regions.get()).allowed())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof Player victim)) return;
        Location from = launchLocation(event.getDamager(), attacker);
        Location to = victim.getLocation();
        if (!policy.canDamage(attacker.getUniqueId(), from.getWorld().getName(), point(from), victim.getUniqueId(),
                to.getWorld().getName(), point(to), regions.get()).allowed()) event.setCancelled(true);
        if (event.getDamager() instanceof Projectile projectile) launches.remove(projectile.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            Location location = player.getLocation();
            launches.put(event.getEntity().getUniqueId(),
                    new LaunchSource(player.getUniqueId(), location.getWorld().getName(), point(location)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        launches.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || sameBlock(from, to)) return;
        if (!policy.canTeleport(event.getPlayer().getUniqueId(), from.getWorld().getName(), point(from),
                to.getWorld().getName(), point(to), false, regions.get()).allowed()) event.setTo(from);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        boolean authorized = permits.consume(event.getPlayer().getUniqueId(), to.getWorld().getName(), point(to),
                clock.instant());
        if (!policy.canTeleport(event.getPlayer().getUniqueId(), from.getWorld().getName(), point(from),
                to.getWorld().getName(), point(to), authorized, regions.get()).allowed()) event.setCancelled(true);
    }

    private static boolean sameBlock(Location first, Location second) {
        return first.getWorld().equals(second.getWorld()) && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY() && first.getBlockZ() == second.getBlockZ();
    }

    private static Point point(Location location) {
        return new Point(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private Location launchLocation(Entity damager, Player fallback) {
        if (!(damager instanceof Projectile projectile)) return fallback.getLocation();
        LaunchSource launch = launches.get(projectile.getUniqueId());
        if (launch == null) return fallback.getLocation();
        org.bukkit.World world = fallback.getServer().getWorld(launch.world());
        return world == null ? fallback.getLocation()
                : new Location(world, launch.point().x(), launch.point().y(), launch.point().z());
    }

    private record LaunchSource(java.util.UUID playerId, String world, Point point) { }
}
