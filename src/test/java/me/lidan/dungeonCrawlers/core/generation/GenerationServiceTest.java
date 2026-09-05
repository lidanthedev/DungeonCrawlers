package me.lidan.dungeonCrawlers.core.generation;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;
import me.lidan.dungeonCrawlers.core.party.PartySnapshot;
import me.lidan.dungeonCrawlers.core.reservation.PlayerReservationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import me.lidan.dungeonCrawlers.integration.GenerationWorldGateway;
import me.lidan.dungeonCrawlers.persistence.DurableRecord;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.DurableSubmission;
import me.lidan.dungeonCrawlers.persistence.DurableWrite;
import me.lidan.dungeonCrawlers.persistence.DurableWriteReceipt;
import me.lidan.dungeonCrawlers.persistence.model.GenerationJournal;
import me.lidan.dungeonCrawlers.persistence.model.GenerationJournalCodec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GenerationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void durableRuntimeAckAlwaysPrecedesFirstPaste() {
        Fixture fixture = fixture(1);
        fixture.repository.autoAck = false;

        var started = fixture.start(UUID.randomUUID());

        assertTrue(started.accepted());
        assertEquals(GenerationService.InstanceStatus.JOURNALING,
                fixture.service.info(started.instanceId()).orElseThrow().status());
        assertEquals(0, fixture.world.pasteCalls);

        fixture.repository.ack.complete(fixture.repository.receipt);

        assertEquals(1, fixture.world.pasteCalls);
        assertEquals(GenerationService.InstanceStatus.GENERATED,
                fixture.service.info(started.instanceId()).orElseThrow().status());
    }

    @Test
    void cancellationDuringPasteWaitsThenClearsBeforeReleaseAndIgnoresRepeat() {
        Fixture fixture = fixture(1);
        CompletableFuture<Void> paste = new CompletableFuture<>();
        fixture.world.pastes.add(paste);
        UUID player = UUID.randomUUID();
        var started = fixture.start(player);

        fixture.service.cancel(started.instanceId());

        assertTrue(fixture.reservations.lookup(player).isPresent());
        assertEquals(SlotAllocator.SlotState.PASTING, fixture.slots.lookup(0).orElseThrow().state());
        assertEquals(0, fixture.world.clearCalls);

        paste.complete(null);

        assertEquals(1, fixture.world.clearCalls);
        assertTrue(fixture.reservations.lookup(player).isEmpty());
        assertEquals(SlotAllocator.SlotState.FREE, fixture.slots.lookup(0).orElseThrow().state());
        assertEquals(GenerationService.InstanceStatus.DESTROYED,
                fixture.service.info(started.instanceId()).orElseThrow().status());
        fixture.service.cancel(started.instanceId());
        assertEquals(1, fixture.world.clearCalls);
    }

    @Test
    void cancellationBeforeJournalAckNeverPastesAndStillRemovesTheJournal() {
        Fixture fixture = fixture(1);
        fixture.repository.autoAck = false;
        UUID player = UUID.randomUUID();
        var started = fixture.start(player);

        fixture.service.cancel(started.instanceId());
        assertTrue(fixture.reservations.lookup(player).isPresent());

        fixture.repository.ack.complete(fixture.repository.receipt);

        assertEquals(0, fixture.world.pasteCalls);
        assertEquals(1, fixture.world.clearCalls);
        assertEquals(1, fixture.repository.deleteCalls);
        assertTrue(fixture.reservations.lookup(player).isEmpty());
        assertEquals(SlotAllocator.SlotState.FREE, fixture.slots.lookup(0).orElseThrow().state());
    }

    @Test
    void generationListenerReceivesTerminalCancellationSnapshot() {
        Fixture fixture = fixture(1);
        fixture.repository.autoAck = false;
        UUID player = UUID.randomUUID();
        var started = fixture.start(player);
        AtomicReference<GenerationService.InstanceSnapshot> callback = new AtomicReference<>();

        assertTrue(fixture.service.whenGenerated(started.instanceId(), callback::set));
        fixture.service.cancel(started.instanceId());
        fixture.repository.ack.complete(fixture.repository.receipt);

        assertEquals(GenerationService.InstanceStatus.DESTROYED, callback.get().status());
        assertTrue(callback.get().detail().contains("slot FREE"));
    }

    @Test
    void cancellationAfterGenerationClearsBeforeFreeing() {
        Fixture fixture = fixture(1);
        UUID player = UUID.randomUUID();
        var started = fixture.start(player);
        assertEquals(GenerationService.InstanceStatus.GENERATED,
                fixture.service.info(started.instanceId()).orElseThrow().status());
        assertEquals(Set.of("start"), fixture.service.activeTemplateIds());

        fixture.service.cancel(started.instanceId());

        assertEquals(1, fixture.world.clearCalls);
        assertTrue(fixture.reservations.lookup(player).isEmpty());
        assertEquals(SlotAllocator.SlotState.FREE, fixture.slots.lookup(0).orElseThrow().state());
        assertTrue(fixture.service.activeTemplateIds().isEmpty());
    }

    @Test
    void pasteFailureClearsJournaledBoundsBeforeReleasing() {
        Fixture fixture = fixture(1);
        fixture.world.pastes.add(CompletableFuture.failedFuture(new IllegalStateException("partial paste")));
        UUID player = UUID.randomUUID();

        var started = fixture.start(player);

        assertTrue(started.accepted());
        assertEquals(1, fixture.world.clearCalls);
        assertEquals(1, fixture.repository.deleteCalls);
        assertTrue(fixture.reservations.lookup(player).isEmpty());
        assertEquals(GenerationService.InstanceStatus.DESTROYED,
                fixture.service.info(started.instanceId()).orElseThrow().status());
    }

    @Test
    void acceptedJournalWithFailedRuntimeAckIsClearedWithoutPasting() {
        Fixture fixture = fixture(1);
        fixture.repository.autoAck = false;
        UUID player = UUID.randomUUID();
        var started = fixture.start(player);

        fixture.repository.ack.completeExceptionally(new IllegalStateException("lost runtime ACK"));

        assertEquals(0, fixture.world.pasteCalls);
        assertEquals(1, fixture.world.clearCalls);
        assertEquals(1, fixture.repository.deleteCalls);
        assertTrue(fixture.reservations.lookup(player).isEmpty());
        assertEquals(GenerationService.InstanceStatus.DESTROYED,
                fixture.service.info(started.instanceId()).orElseThrow().status());
    }

    @Test
    void failedClearKeepsLeaseAndReservationBlockedUntilRetryAck() {
        Fixture fixture = fixture(1);
        CompletableFuture<Void> paste = new CompletableFuture<>();
        fixture.world.pastes.add(paste);
        fixture.world.clears.add(CompletableFuture.failedFuture(new IllegalStateException("clear unavailable")));
        fixture.world.clears.add(CompletableFuture.completedFuture(null));
        UUID player = UUID.randomUUID();
        var started = fixture.start(player);

        fixture.service.cancel(started.instanceId());
        paste.complete(null);

        assertEquals(GenerationService.InstanceStatus.CLEAR_FAILED,
                fixture.service.info(started.instanceId()).orElseThrow().status());
        assertEquals(SlotAllocator.SlotState.CLEARING, fixture.slots.lookup(0).orElseThrow().state());
        assertTrue(fixture.reservations.lookup(player).isPresent());

        fixture.service.cleanup(started.instanceId());

        assertEquals(SlotAllocator.SlotState.FREE, fixture.slots.lookup(0).orElseThrow().state());
        assertTrue(fixture.reservations.lookup(player).isEmpty());
    }

    @Test
    void cleanupDeadlineAlertsOnceWithoutReleasingUnsafeLease() {
        Fixture fixture = fixture(1);
        fixture.world.clears.add(new CompletableFuture<>());
        UUID player = UUID.randomUUID();
        var started = fixture.start(player);

        fixture.service.cancel(started.instanceId());

        var first = fixture.service.checkCleanupDeadlines(
                CLOCK.instant().plus(GenerationService.CLEANUP_DEADLINE));
        var second = fixture.service.checkCleanupDeadlines(
                CLOCK.instant().plus(GenerationService.CLEANUP_DEADLINE).plusSeconds(1));

        assertEquals(1, first.size());
        assertTrue(second.isEmpty());
        assertEquals(SlotAllocator.SlotState.CLEARING, fixture.slots.lookup(0).orElseThrow().state());
        assertTrue(fixture.reservations.lookup(player).isPresent());
        assertEquals(1, fixture.service.operations().cleanupDeadlineAlerts());
    }

    @Test
    void capacityRejectionLeavesNoSecondReservationJournalOrLease() {
        Fixture fixture = fixture(1);
        fixture.world.pastes.add(new CompletableFuture<>());
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        var first = fixture.start(firstPlayer);
        var second = fixture.start(secondPlayer);

        assertTrue(first.accepted());
        assertFalse(second.accepted());
        assertTrue(fixture.reservations.lookup(firstPlayer).isPresent());
        assertTrue(fixture.reservations.lookup(secondPlayer).isEmpty());
        assertEquals(1, fixture.repository.writes.size());
    }

    @Test
    void overlappingStartsForTheSamePlayerHaveExactlyOneWinner() {
        Fixture fixture = fixture(2);
        fixture.world.pastes.add(new CompletableFuture<>());
        UUID player = UUID.randomUUID();

        var first = fixture.start(player);
        var second = fixture.start(player);

        assertTrue(first.accepted());
        assertFalse(second.accepted());
        assertEquals(1, fixture.repository.writes.size());
        assertEquals(1, fixture.slots.snapshot().stream()
                .filter(slot -> slot.state() != SlotAllocator.SlotState.FREE).count());
    }

    @Test
    void startupRecoveryClearsJournalBeforeEnablingStarts() {
        Fixture fixture = fixtureWithoutRecovery(1);
        UUID instanceId = UUID.randomUUID();
        UUID participant = UUID.randomUUID();
        GenerationJournal journal = new GenerationJournal(instanceId, 3, 0, "dungeon_instances",
                List.of(new GenerationJournal.PlannedBounds(1, 2, 3, 4, 5, 6)), List.of(participant),
                "config", "content", "phase2-v1", GenerationJournal.Status.PLANNED, CLOCK.instant());
        byte[] payload = new GenerationJournalCodec().encode(journal);
        fixture.repository.records.add(new DurableRecord(GenerationService.JOURNAL_NAMESPACE, instanceId.toString(),
                payload, "checksum", Path.of("generation", instanceId + ".bin")));

        fixture.service.recover();

        assertTrue(fixture.service.recoveryStatus().startsEnabled());
        assertEquals(1, fixture.service.recoveryStatus().cleared());
        assertEquals(1, fixture.world.clearCalls);
        assertEquals(1, fixture.repository.deleteCalls);
        assertEquals(SlotAllocator.SlotState.FREE, fixture.slots.lookup(0).orElseThrow().state());
    }

    @Test
    void failedStartupClearLeavesOnlyItsLeaseBlockedAndRetryable() {
        Fixture fixture = fixtureWithoutRecovery(2);
        UUID instanceId = UUID.randomUUID();
        GenerationJournal journal = new GenerationJournal(instanceId, 3, 0, "dungeon_instances",
                List.of(new GenerationJournal.PlannedBounds(1, 2, 3, 4, 5, 6)), List.of(UUID.randomUUID()),
                "config", "content", "phase2-v1", GenerationJournal.Status.PLANNED, CLOCK.instant());
        fixture.repository.records.add(new DurableRecord(GenerationService.JOURNAL_NAMESPACE, instanceId.toString(),
                new GenerationJournalCodec().encode(journal), "checksum", Path.of("journal.bin")));
        fixture.world.clears.add(CompletableFuture.failedFuture(new IllegalStateException("injected clear failure")));

        fixture.service.recover();

        assertTrue(fixture.service.recoveryStatus().startsEnabled());
        assertEquals(1, fixture.service.recoveryStatus().blockers().size());
        assertEquals(SlotAllocator.SlotState.CLEARING, fixture.slots.lookup(0).orElseThrow().state());
        assertEquals(SlotAllocator.SlotState.FREE, fixture.slots.lookup(1).orElseThrow().state());
        assertEquals(0, fixture.repository.deleteCalls);
    }

    private static Fixture fixture(int capacity) {
        Fixture fixture = fixtureWithoutRecovery(capacity);
        fixture.service.recover();
        return fixture;
    }

    private static Fixture fixtureWithoutRecovery(int capacity) {
        PlayerReservationService reservations = new PlayerReservationService();
        SlotAllocator slots = new SlotAllocator(new SlotAllocator.Settings(capacity, 10_000, 500, 64, -64, 319));
        FakeRepository repository = new FakeRepository();
        FakeWorld world = new FakeWorld();
        GenerationService.PreparationProvider provider = (instanceId, seed, floor, snapshot, slot) ->
                prepared(instanceId, slot);
        GenerationService service = new GenerationService(reservations, slots, repository, world, provider,
                Runnable::run, Runnable::run, () -> true, ignored -> { }, CLOCK, "dungeon_instances");
        return new Fixture(service, reservations, slots, repository, world);
    }

    private static GenerationService.PreparedGeneration prepared(UUID instanceId, SlotAllocator.SlotLease slot) {
        Point origin = slot.origin();
        Bounds bounds = new Bounds(origin, origin.add(new Point(4, 4, 4)));
        LayoutPlanner.Placement placement = new LayoutPlanner.Placement(0, "start", RoomType.START, null,
                Rotation.NONE, origin, bounds, Optional.empty(), Optional.empty(), Set.of(), Set.of(), List.of(),
                List.of(), List.of(), Optional.empty(), Optional.empty(), Set.of(), List.of());
        LayoutPlanner.LayoutPlan plan = new LayoutPlanner.LayoutPlan("phase2-v1", instanceId, 7, "config",
                "content", List.of(placement), List.of(), List.of("trace"));
        return new GenerationService.PreparedGeneration(plan, Map.of("start", new byte[]{1, 2, 3}));
    }

    private record Fixture(GenerationService service, PlayerReservationService reservations, SlotAllocator slots,
                           FakeRepository repository, FakeWorld world) {
        private GenerationService.StartResult start(UUID player) {
            return service.start(new GenerationService.StartRequest(mock(ConfigSnapshot.class),
                    mock(FloorDefinition.class), new PartySnapshot(player, List.of(player), true), 7, 0));
        }
    }

    private static final class FakeWorld implements GenerationWorldGateway {
        private final Queue<CompletableFuture<Void>> pastes = new ArrayDeque<>();
        private final Queue<CompletableFuture<Void>> clears = new ArrayDeque<>();
        private int pasteCalls;
        private int clearCalls;

        @Override public WorldCheck ensureDedicatedVoidWorld(String worldName) {
            return new WorldCheck(true, "ready", -64, 319);
        }
        @Override public CompletableFuture<Void> paste(String worldName, byte[] schematic, Point origin,
                                                       Rotation rotation) {
            pasteCalls++;
            return pastes.isEmpty() ? CompletableFuture.completedFuture(null) : pastes.remove();
        }
        @Override public CompletableFuture<Void> setupConnections(String worldName,
                                                                  List<LayoutPlanner.Connection> connections) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> clear(String worldName,
                                                       List<GenerationJournal.PlannedBounds> bounds) {
            clearCalls++;
            return clears.isEmpty() ? CompletableFuture.completedFuture(null) : clears.remove();
        }
    }

    private static final class FakeRepository implements DurableRepository {
        private final List<DurableWrite> writes = new ArrayList<>();
        private final List<DurableRecord> records = new ArrayList<>();
        private final CompletableFuture<DurableWriteReceipt> ack = new CompletableFuture<>();
        private final DurableWriteReceipt receipt = new DurableWriteReceipt(UUID.randomUUID(), "key", 1,
                "checksum", Path.of("record.bin"), CLOCK.instant());
        private boolean autoAck = true;
        private int deleteCalls;

        @Override public boolean reserveTerminalLane(UUID instanceId) { return true; }
        @Override public void releaseTerminalLane(UUID instanceId) { }
        @Override public DurableSubmission submit(DurableWrite write) {
            writes.add(write);
            CompletableFuture<DurableWriteReceipt> runtime = autoAck
                    ? CompletableFuture.completedFuture(receipt) : ack;
            return new DurableSubmission(true, CompletableFuture.completedFuture(receipt), runtime, "accepted");
        }
        @Override public DurableSubmission submitTerminal(DurableWrite write) { return submit(write); }
        @Override public CompletableFuture<Optional<DurableRecord>> read(String namespace, String recordId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<List<DurableRecord>> list(String namespace) {
            return CompletableFuture.completedFuture(List.copyOf(records));
        }
        @Override public CompletableFuture<Void> delete(String namespace, String recordId) {
            deleteCalls++;
            records.removeIf(record -> record.namespace().equals(namespace) && record.recordId().equals(recordId));
            return CompletableFuture.completedFuture(null);
        }
        @Override public RepositoryDiagnostics diagnostics() {
            return new RepositoryDiagnostics(10, 0, 0, 0, 0, false);
        }
        @Override public void close() { }
    }
}
