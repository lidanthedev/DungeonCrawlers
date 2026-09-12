package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.generation.SlotAllocator;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.score.DungeonRank;
import me.lidan.dungeonCrawlers.core.score.ScoreService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DungeonGenerationCommandTest {
    @Test
    void runningInstanceShowsLiveStateAndAvailableHoverFields() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000017");
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-12T00:14:32Z");
        var generation = mock(GenerationService.class);
        var runs = mock(RunPreparationService.class);
        var lifecycle = mock(PlayerLifecycleService.class);
        var secrets = mock(SecretDiscoveryService.class);
        var sender = mock(CommandSender.class);
        var run = mock(RunPreparationService.RunSnapshot.class);
        var layout = mock(LayoutPlanner.LayoutPlan.class);
        var score = mock(ScoreService.FinalScoreSnapshot.class);
        var instance = new GenerationService.InstanceSnapshot(id, 7,
                GenerationService.InstanceStatus.GENERATED, List.of(playerId), 918273645L,
                "Floor V", "generation complete");
        when(generation.instances()).thenReturn(List.of(instance));
        when(generation.slots()).thenReturn(List.of(new SlotAllocator.SlotLease(7, id,
                SlotAllocator.SlotState.ALLOCATED, new Point(70000, 64, 0),
                new Bounds(new Point(0, 0, 0), new Point(1, 1, 1)))));
        when(generation.layoutPlan(id)).thenReturn(Optional.of(layout));
        when(layout.placements()).thenReturn(java.util.Collections.nCopies(12, null));
        when(runs.info(id)).thenReturn(Optional.of(run));
        when(run.state()).thenReturn(RunPreparationService.RunState.RUNNING);
        when(run.participants()).thenReturn(List.of(playerId));
        when(run.startedAt()).thenReturn(Instant.parse("2026-09-12T00:00:00Z"));
        when(lifecycle.info(id)).thenReturn(Optional.of(new PlayerLifecycleService.InstanceSnapshot(id,
                true, false, "running", List.of(new PlayerLifecycleService.PlayerSnapshot(playerId,
                PlayerLifecycleService.PlayerState.ALIVE, true, null, null, 2)))));
        when(secrets.info(id)).thenReturn(Optional.of(new SecretDiscoveryService.InstanceSnapshot(id,
                List.of(), Map.of())));
        when(score.total()).thenReturn(286);
        when(score.rank()).thenReturn(DungeonRank.S);

        var command = new DungeonGenerationCommand(null, null, generation, null, "dungeon_instances",
                null, Clock.fixed(now, ZoneOffset.UTC), ignored -> { }, runs, () -> false,
                lifecycle, secrets, ignored -> score);
        command.instances(sender);

        var messages = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(sender, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
        Component row = messages.getAllValues().get(1);
        String visible = PlainTextComponentSerializer.plainText().serialize(row);
        String hover = PlainTextComponentSerializer.plainText().serialize(
                (Component) row.children().getLast().hoverEvent().value());
        assertTrue(visible.contains("Floor V  RUNNING  1 player  14:32"));
        for (String field : List.of("Floor: Floor V", "State: RUNNING", "Alive: 1", "Ghosts: 0",
                "Deaths: 2", "Secrets: 0/0", "Score: 286 (S)", "Runtime: 14m 32s",
                "Rooms: 12", "Slot: 7", "Origin: 70000, 64, 0")) {
            assertTrue(hover.contains(field), () -> "Missing hover field: " + field);
        }
    }

    @Test
    void instanceListKeepsCleanupDiagnosticsOutOfTheVisibleRow() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000016");
        var generation = mock(GenerationService.class);
        var runs = mock(RunPreparationService.class);
        var sender = mock(CommandSender.class);
        var instance = new GenerationService.InstanceSnapshot(id, 7,
                GenerationService.InstanceStatus.DESTROYED, List.of(UUID.randomUUID()),
                918273645L, "Floor V", "clear ACK, journal removed, reservation released, slot FREE");
        when(generation.instances()).thenReturn(List.of(instance));
        when(generation.slots()).thenReturn(List.of());
        when(generation.layoutPlan(id)).thenReturn(Optional.empty());
        when(runs.info(id)).thenReturn(Optional.empty());

        var command = new DungeonGenerationCommand(null, null, generation, null, "dungeon_instances",
                null, Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC),
                ignored -> { }, runs);
        command.instances(sender);

        var messages = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(sender, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
        Component row = messages.getAllValues().get(1);
        Component entry = row.children().getLast();
        String visible = PlainTextComponentSerializer.plainText().serialize(row);
        String hover = PlainTextComponentSerializer.plainText().serialize((Component) entry.hoverEvent().value());
        assertTrue(visible.contains(id + "  Floor V  DESTROYED  1 player  --"));
        assertFalse(visible.contains("status="));
        assertFalse(visible.contains("clear ACK"));
        assertTrue(hover.contains("Instance #" + id));
        assertTrue(hover.contains("Seed: 918273645"));
        assertTrue(hover.contains("Slot: 7"));
        assertEquals("/dungeon instance info " + id, entry.clickEvent().value());
    }
}
