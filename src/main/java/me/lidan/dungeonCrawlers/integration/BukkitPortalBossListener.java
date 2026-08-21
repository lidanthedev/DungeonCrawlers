package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.core.portal.PortalEncounterService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;

import java.util.Objects;
import java.util.UUID;

/** Paper event boundary for portal countdown ownership and boss transition. */
public final class BukkitPortalBossListener implements Listener {
    private final PortalEncounterService encounters;
    private final RunPreparationService runs;
    private final String generationWorldName;

    public BukkitPortalBossListener(PortalEncounterService encounters, RunPreparationService runs,
                                    String generationWorldName) {
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.generationWorldName = Objects.requireNonNull(generationWorldName, "generationWorldName");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getFrom().getWorld() == null
                || !event.getFrom().getWorld().getName().equals(generationWorldName)) return;
        UUID instanceId = portalAt(event.getFrom().getBlockX(), event.getFrom().getBlockY(), event.getFrom().getBlockZ());
        if (instanceId == null && event.getTo() != null) {
            instanceId = portalAt(event.getTo().getBlockX(), event.getTo().getBlockY(), event.getTo().getBlockZ());
        }
        if (instanceId == null) return;
        event.setCancelled(true);
        var result = encounters.enterPortal(instanceId, event.getPlayer().getUniqueId());
        if (!result.successful()) {
            event.getPlayer().sendMessage(MiniMessageUtils.miniMessage("<red>[FAIL] " + result.detail() + "</red>"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() == null
                || !event.getFrom().getWorld().getName().equals(generationWorldName)) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        UUID playerId = event.getPlayer().getUniqueId();
        UUID entered = portalAt(event.getTo().getBlockX(), event.getTo().getBlockY(), event.getTo().getBlockZ());
        UUID instanceId = runs.instanceFor(playerId).orElse(null);
        if (entered != null && instanceId != null && entered.equals(instanceId)) {
            encounters.enterPortal(instanceId, playerId);
        } else if (instanceId != null && encounters.isCountdownOwner(instanceId, playerId)) {
            encounters.ownerLeftPortal(instanceId, playerId);
        }
    }

    private UUID portalAt(int x, int y, int z) {
        return encounters.portalAt(new Point(x, y, z)).orElse(null);
    }
}
