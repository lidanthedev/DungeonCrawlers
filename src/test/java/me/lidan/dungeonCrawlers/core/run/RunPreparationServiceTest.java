package me.lidan.dungeonCrawlers.core.run;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers;
import me.lidan.dungeonCrawlers.core.door.DoorService;
import me.lidan.dungeonCrawlers.core.party.PartySnapshot;
import me.lidan.dungeonCrawlers.core.state.StateTransitionService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunPreparationServiceTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void locksDoorUntilEveryMemberSelectsAndStartsOnce() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        AtomicInteger activations = new AtomicInteger();
        RunPreparationService service = service(activations);
        PartySnapshot party = new PartySnapshot(first, List.of(first, second), false);
        Map<String, ClassDefinition> classes = Map.of("tank", classDefinition("tank"),
                "mage", classDefinition("mage"));

        var registered = service.registerGenerated(instance, party, List.of("tank", "mage"), classes,
                new Point(0, 64, 0), Facing.NORTH);
        assertTrue(registered.successful());
        assertTrue(service.markSnapshotsReady(instance).successful());
        assertFalse(service.selectClass(instance, first, "tank").snapshot().selectedClasses().containsKey(second));
        assertEquals(DoorService.DoorState.LOCKED, service.info(instance).orElseThrow().door().state());
        assertTrue(service.selectClass(instance, second, "mage").successful());
        assertEquals(DoorService.DoorState.READY, service.info(instance).orElseThrow().door().state());

        var opened = service.openDoor(instance, first);
        assertTrue(opened.successful());
        assertEquals(RunPreparationService.RunState.RUNNING, opened.snapshot().state());
        assertEquals(START, opened.snapshot().startedAt());
        assertEquals(1, activations.get());
        assertTrue(service.openDoor(instance, second).successful());
        assertEquals(1, activations.get());
    }

    @Test
    void snapshotAckIsRequiredBeforeClassSelectionOrDoorOpen() {
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        RunPreparationService service = service(new AtomicInteger());
        PartySnapshot party = new PartySnapshot(player, List.of(player), true);
        service.registerGenerated(instance, party, List.of("tank"), Map.of("tank", classDefinition("tank")),
                new Point(0, 64, 0), Facing.SOUTH);

        assertFalse(service.selectClass(instance, player, "tank").successful());
        assertFalse(service.openDoor(instance, player).successful());
        assertTrue(service.markSnapshotsReady(instance).successful());
        assertTrue(service.selectClass(instance, player, "tank").successful());
        assertTrue(service.openDoor(instance, player).successful());
    }

    @Test
    void invalidParticipantsClassesAndDuplicateRegistrationFailClosed() {
        UUID player = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        RunPreparationService service = service(new AtomicInteger());
        PartySnapshot party = new PartySnapshot(player, List.of(player), true);
        Map<String, ClassDefinition> classes = Map.of("tank", classDefinition("tank"));

        assertTrue(service.registerGenerated(instance, party, List.of("tank"), classes,
                new Point(0, 64, 0), Facing.NORTH).successful());
        assertFalse(service.registerGenerated(instance, party, List.of("tank"), classes,
                new Point(0, 64, 0), Facing.NORTH).successful());
        assertTrue(service.markSnapshotsReady(instance).successful());
        assertFalse(service.selectClass(instance, outsider, "tank").successful());
        assertFalse(service.selectClass(instance, player, "mage").successful());
        assertFalse(service.openDoor(instance, outsider).successful());
        assertFalse(service.openDoor(instance, player).successful());
    }

    @Test
    void cleanupRemovesDoorAndCentralRegistration() {
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        RunPreparationService service = new RunPreparationService(new DoorService(), updates,
                new StateTransitionService(), Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        service.registerGenerated(instance, new PartySnapshot(player, List.of(player), true), List.of("tank"),
                Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.EAST);

        assertEquals(1, updates.size());
        assertTrue(service.cleanup(instance).successful());
        assertEquals(0, updates.size());
        assertTrue(service.doorAt(new Point(0, 64, 0)).isEmpty());
    }

    @Test
    void preparationTimeoutCancelsGeneratedInstanceAndRemovesRegistration() {
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        AtomicInteger cancellations = new AtomicInteger();
        List<String> diagnostics = new ArrayList<>();
        RunPreparationService service = new RunPreparationService(new DoorService(), updates,
                new StateTransitionService(), Clock.fixed(START, ZoneOffset.UTC), ignored -> { },
                diagnostics::add, ignored -> cancellations.incrementAndGet());
        service.registerGenerated(instance, new PartySnapshot(player, List.of(player), true), List.of("tank"),
                Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.NORTH);

        var report = updates.tick(START.plus(RunPreparationService.PREPARATION_TIMEOUT));

        assertTrue(report.successful());
        assertTrue(service.info(instance).isEmpty());
        assertEquals(0, updates.size());
        assertEquals(1, cancellations.get());
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("preparation timed out")));
    }

    @Test
    void firstRoomActivationFailureIsRecordedWithoutBlockingDoorStart() {
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        List<String> diagnostics = new ArrayList<>();
        RunPreparationService service = new RunPreparationService(new DoorService(),
                new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { }),
                new StateTransitionService(), Clock.fixed(START, ZoneOffset.UTC),
                ignored -> { throw new IllegalStateException("activation unavailable"); },
                diagnostics::add, ignored -> { });
        service.registerGenerated(instance, new PartySnapshot(player, List.of(player), true), List.of("tank"),
                Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.NORTH);
        service.markSnapshotsReady(instance);
        service.selectClass(instance, player, "tank");

        var opened = service.openDoor(instance, player);

        assertTrue(opened.successful());
        assertTrue(opened.snapshot().firstRoomActivated());
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("first room activation failed")));
    }

    private static RunPreparationService service(AtomicInteger activations) {
        return new RunPreparationService(new DoorService(),
                new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { }),
                new StateTransitionService(), Clock.fixed(START, ZoneOffset.UTC), ignored -> activations.incrementAndGet());
    }

    private static ClassDefinition classDefinition(String id) {
        return new ClassDefinition(id, id, Material.STONE, StatModifiers.empty());
    }
}
