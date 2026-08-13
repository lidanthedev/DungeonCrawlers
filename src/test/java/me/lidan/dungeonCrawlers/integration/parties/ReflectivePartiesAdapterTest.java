package me.lidan.dungeonCrawlers.integration.parties;

import me.lidan.dungeonCrawlers.integration.PartyProvider;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReflectivePartiesAdapterTest {
    @Test
    void absentPluginIsPositiveSolo() {
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.getPlugin("Parties")).thenReturn(null);
        UUID player = UUID.randomUUID();

        PartyProvider.PartyLookup result = new ReflectivePartiesAdapter(server).lookup(player);

        assertEquals(PartyProvider.Status.SOLO, result.status());
        assertEquals(java.util.List.of(player), result.onlineMembers());
    }

    @Test
    void disabledPluginFailsClosed() {
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        Plugin parties = mock(Plugin.class);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.getPlugin("Parties")).thenReturn(parties);
        when(parties.isEnabled()).thenReturn(false);

        PartyProvider.PartyLookup result = new ReflectivePartiesAdapter(server).lookup(UUID.randomUUID());

        assertEquals(PartyProvider.Status.ERROR, result.status());
    }
}
