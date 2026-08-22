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
import java.util.concurrent.atomic.AtomicBoolean;
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
    void escapedParticipantIsRemovedBeforeLaterClassSelectionAndDoorOpen() {
        UUID escaped = UUID.randomUUID();
        UUID remaining = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        RunPreparationService service = service(new AtomicInteger());
        PartySnapshot party = new PartySnapshot(escaped, List.of(escaped, remaining), false);

        assertTrue(service.registerGenerated(instance, party, List.of("tank"),
                Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.NORTH).successful());
        assertTrue(service.markSnapshotsReady(instance).successful());
        assertTrue(service.removeParticipant(instance, escaped).successful());
        assertFalse(service.selectClass(instance, escaped, "tank").successful());
        assertTrue(service.selectClass(instance, remaining, "tank").successful());
        assertTrue(service.openDoor(instance, remaining).successful());
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
    void disableFreezeStopsDeadlineCallbacksAndRejectsNewPreparations() {
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        AtomicInteger cancellations = new AtomicInteger();
        RunPreparationService service = new RunPreparationService(new DoorService(), updates,
                new StateTransitionService(), Clock.fixed(START, ZoneOffset.UTC), ignored -> { },
                ignored -> { }, ignored -> cancellations.incrementAndGet());
        service.registerGenerated(instance, new PartySnapshot(player, List.of(player), true), List.of("tank"),
                Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.NORTH);

        service.freezeForDisable();
        updates.tick(START.plus(RunPreparationService.PREPARATION_TIMEOUT));

        assertTrue(service.frozen());
        assertEquals(0, cancellations.get());
        assertFalse(service.registerGenerated(UUID.randomUUID(), new PartySnapshot(player, List.of(player), true),
                List.of("tank"), Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.NORTH)
                .successful());
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
    void preparationWarningIsEmittedOnceBeforeTimeout() {
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        AtomicInteger cancellations = new AtomicInteger();
        List<RunPreparationService.DeadlineNotice> notices = new ArrayList<>();
        RunPreparationService service = new RunPreparationService(new DoorService(), updates,
                new StateTransitionService(), Clock.fixed(START, ZoneOffset.UTC), ignored -> { },
                ignored -> { }, ignored -> cancellations.incrementAndGet());
        service.configureDeadlineHandlers(ignored -> { }, ignored -> true, notices::add);
        service.registerGenerated(instance, new PartySnapshot(player, List.of(player), true), List.of("tank"),
                Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.NORTH);

        updates.tick(START.plus(RunPreparationService.PREPARATION_WARNING));
        updates.tick(START.plus(RunPreparationService.PREPARATION_WARNING));

        assertEquals(1, notices.stream().filter(value -> value.event()
                == RunPreparationService.DeadlineEvent.PREPARATION_WARNING).count());
        assertTrue(service.info(instance).isPresent());
        updates.tick(START.plus(RunPreparationService.PREPARATION_TIMEOUT));
        assertTrue(service.info(instance).isEmpty());
        assertEquals(1, cancellations.get());
    }

    @Test
    void runDeadlineAppliesToBossAndProvidesFailureReadingPeriod() {
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger cleanups = new AtomicInteger();
        List<RunPreparationService.DeadlineNotice> notices = new ArrayList<>();
        RunPreparationService service = new RunPreparationService(new DoorService(), updates,
                new StateTransitionService(), Clock.fixed(START, ZoneOffset.UTC), ignored -> { },
                ignored -> { }, ignored -> cleanups.incrementAndGet());
        service.configureDeadlineHandlers(ignored -> failures.incrementAndGet(), ignored -> true, notices::add);
        service.registerGenerated(instance, new PartySnapshot(player, List.of(player), true), List.of("tank"),
                Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.NORTH);
        service.markSnapshotsReady(instance);
        service.selectClass(instance, player, "tank");
        service.openDoor(instance, player);
        service.enterBoss(instance);

        updates.tick(START.plus(RunPreparationService.RUN_WARNING));
        updates.tick(START.plus(RunPreparationService.RUN_WARNING));
        assertEquals(RunPreparationService.RunState.BOSS, service.info(instance).orElseThrow().state());
        assertEquals(1, notices.stream().filter(value -> value.event()
                == RunPreparationService.DeadlineEvent.RUN_WARNING).count());

        updates.tick(START.plus(RunPreparationService.RUN_TIMEOUT));
        assertEquals(RunPreparationService.RunState.FAILED, service.info(instance).orElseThrow().state());
        assertEquals(1, failures.get());
        assertEquals(START.plus(RunPreparationService.RUN_TIMEOUT)
                        .plus(RunPreparationService.FAILED_READING_PERIOD),
                service.info(instance).orElseThrow().failedDeadline());

        updates.tick(START.plus(RunPreparationService.RUN_TIMEOUT)
                .plus(RunPreparationService.FAILED_READING_PERIOD));
        assertTrue(service.info(instance).isEmpty());
        assertEquals(1, cleanups.get());
    }

    @Test
    void completedRewardPeriodWarnsCountsDownAndCloses() {
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        AtomicInteger cleanups = new AtomicInteger();
        AtomicBoolean active = new AtomicBoolean(true);
        List<RunPreparationService.DeadlineNotice> notices = new ArrayList<>();
        RunPreparationService service = new RunPreparationService(new DoorService(), updates,
                new StateTransitionService(), Clock.fixed(START, ZoneOffset.UTC), ignored -> { },
                ignored -> { }, ignored -> cleanups.incrementAndGet());
        service.configureDeadlineHandlers(ignored -> { }, ignored -> active.get(), notices::add);
        service.registerGenerated(instance, new PartySnapshot(player, List.of(player), true), List.of("tank"),
                Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.NORTH);
        service.markSnapshotsReady(instance);
        service.selectClass(instance, player, "tank");
        service.openDoor(instance, player);
        service.enterBoss(instance);
        service.enterCompletionPending(instance);
        assertTrue(service.markCompleted(instance).successful());

        Instant warning = START.plus(RunPreparationService.COMPLETION_TIMEOUT)
                .minus(RunPreparationService.COMPLETION_WARNING);
        updates.tick(warning);
        updates.tick(warning);
        assertEquals(1, notices.stream().filter(value -> value.event()
                == RunPreparationService.DeadlineEvent.COMPLETION_WARNING).count());
        Instant finalCountdown = START.plus(RunPreparationService.COMPLETION_TIMEOUT)
                .minus(RunPreparationService.COMPLETION_FINAL_COUNTDOWN);
        updates.tick(finalCountdown);
        updates.tick(finalCountdown);
        assertEquals(1, notices.stream().filter(value -> value.event()
                == RunPreparationService.DeadlineEvent.COMPLETION_COUNTDOWN).count());
        assertEquals(RunPreparationService.RunState.COMPLETED, service.info(instance).orElseThrow().state());

        updates.tick(START.plus(RunPreparationService.COMPLETION_TIMEOUT));
        assertTrue(service.info(instance).isEmpty());
        assertEquals(1, cleanups.get());
    }

    @Test
    void completedRewardPeriodClosesEarlyWhenActiveGroupIsEmpty() {
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger cleanups = new AtomicInteger();
        RunPreparationService service = new RunPreparationService(new DoorService(), updates,
                new StateTransitionService(), Clock.fixed(START, ZoneOffset.UTC), ignored -> { },
                ignored -> { }, ignored -> cleanups.incrementAndGet());
        service.configureDeadlineHandlers(ignored -> { }, ignored -> active.get(), ignored -> { });
        service.registerGenerated(instance, new PartySnapshot(player, List.of(player), true), List.of("tank"),
                Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.NORTH);
        service.markSnapshotsReady(instance);
        service.selectClass(instance, player, "tank");
        service.openDoor(instance, player);
        service.enterBoss(instance);
        service.enterCompletionPending(instance);
        service.markCompleted(instance);
        active.set(false);

        updates.tick(START.plusSeconds(1));

        assertTrue(service.info(instance).isEmpty());
        assertEquals(1, cleanups.get());
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

    @Test
    void recoveryAwareActivationFailureCleansPreparationAndInvokesCancellation() {
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        AtomicInteger cancellations = new AtomicInteger();
        RunPreparationService service = new RunPreparationService(new DoorService(),
                new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { }),
                new StateTransitionService(), Clock.fixed(START, ZoneOffset.UTC),
                ignored -> { throw new IllegalStateException("spawn failed"); },
                ignored -> { }, ignored -> cancellations.incrementAndGet(), true);
        service.registerGenerated(instance, new PartySnapshot(player, List.of(player), true), List.of("tank"),
                Map.of("tank", classDefinition("tank")), new Point(0, 64, 0), Facing.NORTH);
        service.markSnapshotsReady(instance);
        service.selectClass(instance, player, "tank");

        var opened = service.openDoor(instance, player);

        assertFalse(opened.successful());
        assertTrue(opened.rollbackRequired());
        assertTrue(service.info(instance).isEmpty());
        assertEquals(1, cancellations.get());
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
