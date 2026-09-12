package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DungeonPlaceholderExpansionTest {
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000041");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000043");

    @BeforeEach
    void setUpBukkit() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void instanceThisResolvesUsingThePlaceholderPlayer() {
        RunPreparationService runs = mock(RunPreparationService.class);
        RunPreparationService.RunSnapshot run = mock(RunPreparationService.RunSnapshot.class);
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(PLAYER);
        when(run.instanceId()).thenReturn(INSTANCE);
        when(run.state()).thenReturn(RunPreparationService.RunState.RUNNING);
        when(runs.instanceFor(PLAYER)).thenReturn(Optional.of(INSTANCE));
        when(runs.info(INSTANCE)).thenReturn(Optional.of(run));

        DungeonPlaceholderExpansion expansion = expansion(runs);

        assertEquals("running", expansion.onRequest(player, "instance_this_state"));
        assertEquals("true", expansion.onRequest(player, "instance_this_exists"));
        assertEquals(INSTANCE.toString(), expansion.onRequest(player, "instance_this_id"));
        assertEquals("running", expansion.onRequest(player, "instance_" + INSTANCE + "_state"));
    }

    @Test
    void instanceThisUsesMissingInstanceFallbackOutsideADungeon() {
        RunPreparationService runs = mock(RunPreparationService.class);
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(OUTSIDER);
        when(runs.instanceFor(OUTSIDER)).thenReturn(Optional.empty());

        DungeonPlaceholderExpansion expansion = expansion(runs);

        assertEquals("false", expansion.onRequest(player, "instance_this_exists"));
        assertEquals("0", expansion.onRequest(player, "instance_this_players"));
        assertEquals("", expansion.onRequest(player, "instance_this_state"));
    }

    private static DungeonPlaceholderExpansion expansion(RunPreparationService runs) {
        return new DungeonPlaceholderExpansion(
                mock(JavaPlugin.class),
                mock(GenerationService.class),
                runs,
                mock(PlayerLifecycleService.class),
                mock(SecretDiscoveryService.class),
                new DebugSettings(false),
                ignored -> null);
    }
}
