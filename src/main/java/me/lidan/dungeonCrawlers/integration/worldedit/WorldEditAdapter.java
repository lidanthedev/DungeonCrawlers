package me.lidan.dungeonCrawlers.integration.worldedit;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.regions.Region;
import me.lidan.dungeonCrawlers.integration.WorldEditGateway;

public final class WorldEditAdapter implements WorldEditGateway {
    @Override
    public SelectionResult selection(org.bukkit.entity.Player player) {
        Player actor = BukkitAdapter.adapt(player);
        try {
            Region region = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(actor.getWorld());
            return new SelectionResult(true, "selection=" + region.getMinimumPoint() + ".." + region.getMaximumPoint()
                    + ", volume=" + region.getVolume());
        } catch (IncompleteRegionException exception) {
            return new SelectionResult(false, "WorldEdit selection is incomplete");
        }
    }
}
