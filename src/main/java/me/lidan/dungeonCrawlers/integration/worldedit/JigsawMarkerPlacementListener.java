package me.lidan.dungeonCrawlers.integration.worldedit;

import me.lidan.dungeonCrawlers.authoring.RoomMarkerItemFactory;
import me.lidan.dungeonCrawlers.core.template.RoomMarker;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Objects;

/** Applies native Jigsaw block-entity fields after an author places a kit Jigsaw. */
public final class JigsawMarkerPlacementListener implements Listener {
    private final RoomMarkerItemFactory items;

    public JigsawMarkerPlacementListener(RoomMarkerItemFactory items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        RoomMarker marker = items.marker(event.getItemInHand()).orElse(null);
        if (marker != RoomMarker.ENTRANCE && marker != RoomMarker.EXIT) return;
        if (event.getBlockPlaced().getType() != Material.JIGSAW) return;
        String name = marker == RoomMarker.ENTRANCE ? "dungeoncrawlers:entrance" : "dungeoncrawlers:exit";
        if (WorldEditAdapter.configureJigsaw(event.getBlockPlaced(), name)) return;
        event.setCancelled(true);
        DungeonMessages.send(event.getPlayer(), DungeonMessages.error(
                "The Jigsaw marker could not be configured; placement was cancelled."));
    }
}
