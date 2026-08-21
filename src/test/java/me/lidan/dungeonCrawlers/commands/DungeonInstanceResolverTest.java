package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class DungeonInstanceResolverTest {
    @Test
    void resolvesUuidWithoutInspectingSender() {
        CommandSender sender = mock(CommandSender.class);
        RunPreparationService runs = mock(RunPreparationService.class);
        UUID instance = UUID.randomUUID();

        assertEquals(instance, DungeonInstanceResolver.require(sender, instance.toString(), runs));
    }

    @Test
    void resolvesThisFromPlayersActiveRun() {
        Player player = mock(Player.class);
        RunPreparationService runs = mock(RunPreparationService.class);
        UUID playerId = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(runs.instanceFor(playerId)).thenReturn(Optional.of(instance));

        assertEquals(instance, DungeonInstanceResolver.require(player, "this", runs));
    }

    @Test
    void rejectsThisForConsoleAndPlayersOutsideRuns() {
        CommandSender sender = mock(CommandSender.class);
        RunPreparationService runs = mock(RunPreparationService.class);
        assertThrows(IllegalArgumentException.class,
                () -> DungeonInstanceResolver.require(sender, "this", runs));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(runs.instanceFor(player.getUniqueId())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> DungeonInstanceResolver.require(player, "this", runs));
    }
}
