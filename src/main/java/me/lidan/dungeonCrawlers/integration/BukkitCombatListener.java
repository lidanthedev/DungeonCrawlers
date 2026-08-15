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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Material;

import java.util.Objects;

/** Paper event boundary for room doors and required mob reconciliation. */
public final class BukkitCombatListener implements Listener {
    private final CombatRoomService combat;
    private final BukkitEntityIdentity identity;
    private final String generationWorldName;

    public BukkitCombatListener(CombatRoomService combat, BukkitEntityIdentity identity,
                                String generationWorldName) {
        this.combat = Objects.requireNonNull(combat, "combat");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.generationWorldName = Objects.requireNonNull(generationWorldName, "generationWorldName");
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
        EntityIdentity value = identity.read(event.getEntity()).orElse(null);
        if (value == null) return;
        combat.onRemoved(value.instanceId(), value.roomIndex(), event.getEntity().getUniqueId());
    }
}
