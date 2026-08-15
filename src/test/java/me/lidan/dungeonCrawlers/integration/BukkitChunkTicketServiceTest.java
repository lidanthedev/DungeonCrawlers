package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.chunk.ChunkTicketBudget;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitChunkTicketServiceTest {
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
