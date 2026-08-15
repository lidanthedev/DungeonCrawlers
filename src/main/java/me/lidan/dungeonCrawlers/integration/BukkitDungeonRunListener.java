package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.stats.StatsCalculateEvent;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseFiveCommand;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.stats.StatAggregationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Main-thread boundary for preparation-door interaction and transient run stats. */
public final class BukkitDungeonRunListener implements Listener {
    private final DungeonPhaseFiveCommand phaseFive;
    private final RunPreparationService runs;
    private final StatAggregationService stats = new StatAggregationService();
    private final String generationWorldName;

    public BukkitDungeonRunListener(DungeonPhaseFiveCommand phaseFive, RunPreparationService runs,
                                    String generationWorldName) {
        this.phaseFive = Objects.requireNonNull(phaseFive, "phaseFive");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.generationWorldName = Objects.requireNonNull(generationWorldName, "generationWorldName");
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
        var aggregated = stats.aggregate(incoming, selected, java.util.Map.of(), java.util.Map.of());
        aggregated.forEach((type, value) -> event.getStats().set(type, value));
    }
}
