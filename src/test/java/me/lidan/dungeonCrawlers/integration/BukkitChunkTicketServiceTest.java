package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.chunk.ChunkTicketBudget;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitChunkTicketServiceTest {
    @Test
    void recoveryWaitsForChunkEntitiesAndWorldToBeLoaded() {
        var plugin = mock(Plugin.class);
        var server = mock(org.bukkit.Server.class);
        var world = mock(World.class);
        var chunk = mock(org.bukkit.Chunk.class);
        UUID worldId = UUID.randomUUID();
        when(plugin.getServer()).thenReturn(server);
        when(world.getUID()).thenReturn(worldId);
        when(server.getWorld(worldId)).thenReturn(world);
        when(world.getChunkAt(0, 0)).thenReturn(chunk);
        var service = new BukkitChunkTicketService(plugin, world, new ChunkTicketBudget(4, 4));
        var bounds = new me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds(
                new me.lidan.dungeonCrawlers.core.template.TemplateModels.Point(0, 0, 0),
                new me.lidan.dungeonCrawlers.core.template.TemplateModels.Point(15, 10, 15));
        assertFalse(service.isLoaded(bounds));
        verify(world, never()).getChunkAt(0, 0);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        assertFalse(service.isLoaded(bounds));
        when(chunk.isEntitiesLoaded()).thenReturn(true);
        assertTrue(service.isLoaded(bounds));
        when(server.getWorld(worldId)).thenReturn(null);
        assertFalse(service.isLoaded(bounds));
    }
    @Test
    void releaseRemovesOnlyTicketsHeldByTheInstance() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        when(world.addPluginChunkTicket(anyInt(), anyInt(), same(plugin))).thenReturn(true);
        BukkitChunkTicketService service = new BukkitChunkTicketService(plugin, world,
                new ChunkTicketBudget(4, 4));
        UUID instance = UUID.randomUUID();
        var held = new ChunkTicketBudget.ChunkKey(1, 2);
        var foreign = new ChunkTicketBudget.ChunkKey(9, 9);
        service.acquire(instance, List.of(held));

        assertEquals(1, service.release(instance, List.of(held, foreign)));

        verify(world).removePluginChunkTicket(held.x(), held.z(), plugin);
        verify(world, never()).removePluginChunkTicket(foreign.x(), foreign.z(), plugin);
    }
}
