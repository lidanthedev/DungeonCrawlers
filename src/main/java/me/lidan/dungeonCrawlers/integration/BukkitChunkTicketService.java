package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.chunk.ChunkTicketBudget;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public final class BukkitChunkTicketService {
    private final Plugin plugin;
    private final World world;
    private final ChunkTicketBudget budget;

    public BukkitChunkTicketService(Plugin plugin, World world, ChunkTicketBudget budget) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.world = Objects.requireNonNull(world, "world");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public synchronized boolean acquire(UUID instanceId, Collection<ChunkTicketBudget.ChunkKey> requested) {
        if (!budget.acquire(instanceId, requested)) return false;
        int added = 0;
        try {
            for (ChunkTicketBudget.ChunkKey chunk : requested) {
                if (!world.addPluginChunkTicket(chunk.x(), chunk.z(), plugin)) {
                    throw new IllegalStateException("world rejected chunk ticket " + chunk);
                }
                added++;
            }
            return true;
        } catch (RuntimeException exception) {
            for (ChunkTicketBudget.ChunkKey chunk : requested.stream().limit(added).toList()) {
                world.removePluginChunkTicket(chunk.x(), chunk.z(), plugin);
            }
            budget.release(instanceId, requested);
            return false;
        }
    }

    public synchronized int release(UUID instanceId, Collection<ChunkTicketBudget.ChunkKey> requested) {
        int released = budget.release(instanceId, requested);
        requested.forEach(chunk -> world.removePluginChunkTicket(chunk.x(), chunk.z(), plugin));
        return released;
    }

    public synchronized int releaseAll(UUID instanceId) {
        var held = budget.tickets(instanceId);
        int released = budget.releaseAll(instanceId);
        if (released > 0) {
            held.forEach(chunk -> world.removePluginChunkTicket(chunk.x(), chunk.z(), plugin));
        }
        return released;
    }

    public ChunkTicketBudget budget() { return budget; }
}
