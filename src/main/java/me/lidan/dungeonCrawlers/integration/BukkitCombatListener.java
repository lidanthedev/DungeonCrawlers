package me.lidan.dungeonCrawlers.integration;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.identity.EntityIdentity;
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
    private final Set<UUID> unloadingWorlds = ConcurrentHashMap.newKeySet();

    public BukkitCombatListener(CombatRoomService combat, BukkitEntityIdentity identity,
                                String generationWorldName) {
        this(combat, identity, generationWorldName, () -> false);
    }

    public BukkitCombatListener(CombatRoomService combat, BukkitEntityIdentity identity,
                                String generationWorldName, BooleanSupplier shuttingDown) {
        this.combat = Objects.requireNonNull(combat, "combat");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.generationWorldName = Objects.requireNonNull(generationWorldName, "generationWorldName");
        this.shuttingDown = Objects.requireNonNull(shuttingDown, "shuttingDown");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
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
        event.getPlayer().sendMessage("[" + (result.successful() ? "PASS" : "FAIL") + "] " + result.detail());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
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
}
