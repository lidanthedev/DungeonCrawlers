package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.chunk.ChunkTicketBudget;
import me.lidan.dungeonCrawlers.core.combat.CombatChunkGateway;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class BukkitChunkTicketService implements CombatChunkGateway {
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
        Set<ChunkTicketBudget.ChunkKey> held = budget.tickets(instanceId);
        Set<ChunkTicketBudget.ChunkKey> removable = new LinkedHashSet<>(requested);
        removable.retainAll(held);
        int released = budget.release(instanceId, requested);
        removable.forEach(chunk -> world.removePluginChunkTicket(chunk.x(), chunk.z(), plugin));
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

    @Override
    public boolean acquire(UUID instanceId, Bounds bounds) {
        return acquire(instanceId, chunks(bounds));
    }

    @Override
    public int release(UUID instanceId, Bounds bounds) {
        return release(instanceId, chunks(bounds));
    }

    private static Set<ChunkTicketBudget.ChunkKey> chunks(Bounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        Set<ChunkTicketBudget.ChunkKey> result = new LinkedHashSet<>();
        int minX = Math.floorDiv(bounds.minimum().x(), 16);
        int maxX = Math.floorDiv(bounds.maximum().x(), 16);
        int minZ = Math.floorDiv(bounds.minimum().z(), 16);
        int maxZ = Math.floorDiv(bounds.maximum().z(), 16);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) result.add(new ChunkTicketBudget.ChunkKey(x, z));
        }
        return Set.copyOf(result);
    }
}
