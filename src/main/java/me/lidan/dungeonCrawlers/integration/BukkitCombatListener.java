package me.lidan.dungeonCrawlers.integration;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.identity.EntityIdentity;
import me.lidan.dungeonCrawlers.core.portal.PortalEncounterService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Material;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Paper event boundary for room doors and required mob reconciliation. */
public final class BukkitCombatListener implements Listener {
    private final CombatRoomService combat;
    private final BukkitEntityIdentity identity;
    private final String generationWorldName;
    private final BooleanSupplier shuttingDown;
    private final BukkitBossIdentity bossIdentity;
    private final PortalEncounterService encounters;
    private final Set<UUID> unloadingWorlds = ConcurrentHashMap.newKeySet();

    public BukkitCombatListener(CombatRoomService combat, BukkitEntityIdentity identity,
                                String generationWorldName, BooleanSupplier shuttingDown,
                                BukkitBossIdentity bossIdentity, PortalEncounterService encounters) {
        this.combat = Objects.requireNonNull(combat, "combat");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.generationWorldName = Objects.requireNonNull(generationWorldName, "generationWorldName");
        this.shuttingDown = Objects.requireNonNull(shuttingDown, "shuttingDown");
        this.bossIdentity = Objects.requireNonNull(bossIdentity, "bossIdentity");
        this.encounters = Objects.requireNonNull(encounters, "encounters");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRoomDoor(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || !event.getClickedBlock().getWorld().getName().equals(generationWorldName)) return;
        Point point = new Point(event.getClickedBlock().getX(), event.getClickedBlock().getY(),
                event.getClickedBlock().getZ());
        if (!combat.isDoorAt(point)) return;
        event.setCancelled(true);
        CombatRoomService.ActivationResult result = combat.activateAt(point);
        if (result.successful()) {
            result.openedDoorBlocks().forEach(opened -> event.getClickedBlock().getWorld()
                    .getBlockAt(opened.x(), opened.y(), opened.z()).setType(Material.AIR, false));
        }
        DungeonMessages.send(event.getPlayer(), result.successful()
                ? DungeonMessages.success(playerMessage(result.detail()))
                : DungeonMessages.error(playerMessage(result.detail())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (bossIdentity != null && encounters != null) {
            UUID bossInstance = bossIdentity.read(event.getEntity()).orElse(null);
            if (bossInstance != null) {
                encounters.onBossDeath(bossInstance, event.getEntity().getUniqueId());
                return;
            }
        }
        EntityIdentity value = identity.read(event.getEntity()).orElse(null);
        if (value == null) return;
        combat.onDeath(value.instanceId(), value.roomIndex(), event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobRemoved(EntityRemoveFromWorldEvent event) {
        if (shuttingDown.getAsBoolean()
                || unloadingWorlds.contains(event.getEntity().getWorld().getUID())) return;
        EntityIdentity value = identity.read(event.getEntity()).orElse(null);
        if (value == null) return;
        combat.onRemoved(value.instanceId(), value.roomIndex(), event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        if (!event.isCancelled()) unloadingWorlds.add(event.getWorld().getUID());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        unloadingWorlds.remove(event.getWorld().getUID());
    }

    private static String playerMessage(String detail) {
        String value = detail == null ? "" : detail;
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("room active; required mobs=")) {
            return "Room opened. Required enemies: " + value.substring("room active; required mobs=".length()) + ".";
        }
        if (normalized.equals("room already active")) return "This room is already open.";
        if (normalized.equals("room already cleared")) return "This room has already been cleared.";
        if (normalized.equals("portal room ready")) return "The portal room is ready.";
        if (normalized.contains("previous room") && normalized.contains("is not cleared")) {
            return "Clear the previous room before opening this door.";
        }
        if (normalized.contains("is locked")) return "This room is still locked.";
        if (normalized.contains("chunk-ticket budget") || normalized.contains("spawn exhausted")) {
            return "This room could not be opened right now. Please try again later.";
        }
        if (normalized.startsWith("unknown combat") || normalized.contains("no combat door")) {
            return "This dungeon door is no longer active.";
        }
        return "This dungeon door could not be opened right now.";
    }
}
