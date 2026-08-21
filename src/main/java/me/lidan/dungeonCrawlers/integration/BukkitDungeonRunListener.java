package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.stats.StatsCalculateEvent;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseFiveCommand;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread boundary for preparation-door interaction and transient run stats. */
public final class BukkitDungeonRunListener implements Listener {
    private final DungeonPhaseFiveCommand phaseFive;
    private final RunPreparationService runs;
    private final SecretDiscoveryService phaseSeven;
    private final String generationWorldName;

    public BukkitDungeonRunListener(DungeonPhaseFiveCommand phaseFive, RunPreparationService runs,
                                    String generationWorldName) {
        this(phaseFive, runs, generationWorldName, null);
    }

    public BukkitDungeonRunListener(DungeonPhaseFiveCommand phaseFive, RunPreparationService runs,
                                    String generationWorldName, SecretDiscoveryService phaseSeven) {
        this.phaseFive = Objects.requireNonNull(phaseFive, "phaseFive");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.generationWorldName = Objects.requireNonNull(generationWorldName, "generationWorldName");
        this.phaseSeven = phaseSeven;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreparationDoor(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !event.getClickedBlock().getWorld().getName().equals(generationWorldName)) return;
        Point point = new Point(event.getClickedBlock().getX(), event.getClickedBlock().getY(),
                event.getClickedBlock().getZ());
        if (runs.doorAt(point).isEmpty()) return;
        event.setCancelled(true);
        phaseFive.openDoorAt(event.getPlayer(), point);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStatsCalculate(StatsCalculateEvent event) {
        ClassDefinition selected = phaseFive.selectedClass(event.getPlayer().getUniqueId()).orElse(null);
        if (selected == null) return;
        Map<StatType, Double> incoming = new LinkedHashMap<>();
        for (StatType type : StatType.values()) {
            var value = event.getStats().get(type);
            if (value != null) incoming.put(type, value.getValue());
        }
        UUID instanceId = runs.instanceFor(event.getPlayer().getUniqueId()).orElse(null);
        var aggregated = phaseSeven == null || instanceId == null
                ? new me.lidan.dungeonCrawlers.core.stats.StatAggregationService().aggregate(
                        incoming, selected, java.util.Map.of(), java.util.Map.of())
                : phaseSeven.aggregate(instanceId, selected, incoming);
        aggregated.forEach((type, value) -> event.getStats().set(type, value));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSecretInteract(PlayerInteractEvent event) {
        if (phaseSeven == null || event.getHand() == EquipmentSlot.OFF_HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !event.getClickedBlock().getWorld().getName().equals(generationWorldName)) return;
        org.bukkit.Material material = event.getClickedBlock().getType();
        if (material != org.bukkit.Material.CHEST && material != org.bukkit.Material.TRAPPED_CHEST) return;
        UUID instanceId = runs.instanceFor(event.getPlayer().getUniqueId()).orElse(null);
        if (instanceId == null) return;
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
            return;
        }
        Point point = new Point(event.getClickedBlock().getX(), event.getClickedBlock().getY(),
                event.getClickedBlock().getZ());
        var result = phaseSeven.discover(instanceId, event.getPlayer().getUniqueId(), point);
        if (!result.successful()) return;
        event.setCancelled(true);
        if (result.status() == SecretDiscoveryService.Status.ALREADY_DISCOVERED) {
            event.getPlayer().sendMessage(MiniMessageUtils.miniMessage("<yellow>Secret already found.</yellow>"));
            return;
        }
        if (result.blessingId() == null) {
            event.getPlayer().sendMessage(MiniMessageUtils.miniMessage("<green>Secret discovered.</green>"));
        } else {
            var discovery = result.blessing();
            String displayName = phaseSeven.blessingDisplayName(instanceId, result.blessingId())
                    .orElse(result.blessingId());
            event.getPlayer().sendMessage(MiniMessageUtils.miniMessage("<light_purple>Blessing discovered: "
                    + displayName + " <gray>level " + discovery.levelsAwarded()
                    + (discovery.atCap() ? " (max)" : "") + ".</light_purple>"));
        }
    }
}
