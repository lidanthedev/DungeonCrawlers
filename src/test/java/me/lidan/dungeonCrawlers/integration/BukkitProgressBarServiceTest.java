package me.lidan.dungeonCrawlers.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitProgressBarServiceTest {
    private static final UUID TASK = UUID.fromString("00000000-0000-0000-0000-000000000051");

    @BeforeEach
    void setUpBukkit() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void progressBarsAreShownOnlyToAdministrators() throws ReflectiveOperationException {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(MockBukkit.getMock());
        Player regular = player(false);
        Player administrator = player(true);

        BukkitProgressBarService service = new BukkitProgressBarService(plugin);
        service.begin(TASK, List.of(regular, administrator),
                "Dungeon generation", "pasting dungeon", 0.72);

        var activeField = BukkitProgressBarService.class.getDeclaredField("active");
        activeField.setAccessible(true);
        Object task = ((java.util.Map<?, ?>) activeField.get(service)).get(TASK);
        var barsField = task.getClass().getDeclaredField("bars");
        barsField.setAccessible(true);
        assertEquals(1, ((java.util.Map<?, ?>) barsField.get(task)).size());
    }

    private static Player player(boolean administrator) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("dungeoncrawlers.admin")).thenReturn(administrator);
        return player;
    }
}
