package me.lidan.dungeonCrawlers.core.lifecycle;

import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLifecycleServiceTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void lethalPlayerBecomesGhostAndRevivesOnceNearAliveMember() {
        UUID instance = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        List<PlayerLifecycleService.Notice> notices = new ArrayList<>();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        PlayerLifecycleService service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC),
                notices::add);
        assertTrue(updates.register(instance, ignored -> { }));
        assertTrue(service.register(instance, List.of(ghost, alive)).successful());
        assertTrue(service.start(instance).successful());

        var death = service.lethal(instance, ghost, START);
        assertEquals(PlayerLifecycleService.PlayerState.GHOST, service.player(instance, ghost).orElseThrow().state());
        assertEquals(1, service.player(instance, ghost).orElseThrow().deaths());
        assertEquals(START.plusSeconds(60), service.player(instance, ghost).orElseThrow().reviveAt());
        assertTrue(death.successful());

        updates.tick(START.plusSeconds(59));
        assertEquals(PlayerLifecycleService.PlayerState.GHOST, service.player(instance, ghost).orElseThrow().state());
        assertEquals(1, notices.stream().filter(value -> value.event() == PlayerLifecycleService.Event.GHOST_COUNTDOWN).count());
        assertEquals("Reviving in 1 second", notices.stream()
                .filter(value -> value.event() == PlayerLifecycleService.Event.GHOST_COUNTDOWN)
                .findFirst().orElseThrow().detail());
        updates.tick(START.plusSeconds(59));
        assertEquals(1, notices.stream().filter(value -> value.event() == PlayerLifecycleService.Event.GHOST_COUNTDOWN).count());
        updates.tick(START.plusSeconds(60));

        var revived = service.player(instance, ghost).orElseThrow();
        assertEquals(PlayerLifecycleService.PlayerState.ALIVE, revived.state());
        assertEquals(alive, revived.reviveTarget());
        assertEquals(1, notices.stream().filter(value -> value.event() == PlayerLifecycleService.Event.REVIVED).count());
    }

    @Test
    void deathCountIncrementsOnlyForAcceptedLethalTransitions() {
        UUID instance = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        PlayerLifecycleService service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC),
                ignored -> { });
        assertTrue(updates.register(instance, ignored -> { }));
        assertTrue(service.register(instance, List.of(ghost, alive)).successful());
        assertTrue(service.start(instance).successful());

        assertTrue(service.lethal(instance, ghost, START).successful());
        assertFalse(service.lethal(instance, ghost, START).successful());
        assertEquals(1, service.player(instance, ghost).orElseThrow().deaths());
    }

    @Test
    void logoutPreservesGhostDeadlineAndSoloLossWipesRun() {
        UUID instance = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        PlayerLifecycleService service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC),
                ignored -> { });
        assertTrue(updates.register(instance, ignored -> { }));
        assertTrue(service.register(instance, List.of(ghost, alive)).successful());
        assertTrue(service.start(instance).successful());
        service.lethal(instance, ghost, START);

        assertTrue(service.disconnect(instance, ghost).successful());
        assertEquals(START.plusSeconds(60), service.player(instance, ghost).orElseThrow().reviveAt());
        assertTrue(service.reconnect(instance, ghost).successful());
        assertEquals(START.plusSeconds(60), service.player(instance, ghost).orElseThrow().reviveAt());

        assertTrue(service.disconnect(instance, alive).successful());
        assertTrue(service.info(instance).orElseThrow().wiped());
    }

    @Test
    void disconnectingAlivePlayerBecomesGhostAndCountsAsDeath() {
        UUID instance = UUID.randomUUID();
        UUID disconnected = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        PlayerLifecycleService service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC),
                ignored -> { });
        assertTrue(updates.register(instance, ignored -> { }));
        assertTrue(service.register(instance, List.of(disconnected, alive)).successful());
        assertTrue(service.start(instance).successful());

        var result = service.disconnect(instance, disconnected);

        assertTrue(result.successful());
        assertEquals(PlayerLifecycleService.Event.DISCONNECTED, result.event());
        var snapshot = service.player(instance, disconnected).orElseThrow();
        assertEquals(PlayerLifecycleService.PlayerState.GHOST, snapshot.state());
        assertFalse(snapshot.online());
        assertEquals(1, snapshot.deaths());
        assertEquals(START.plus(PlayerLifecycleService.REVIVE_DURATION), snapshot.reviveAt());

        assertTrue(service.reconnect(instance, disconnected).successful());
        var reconnected = service.player(instance, disconnected).orElseThrow();
        assertEquals(PlayerLifecycleService.PlayerState.GHOST, reconnected.state());
        assertTrue(reconnected.online());
        assertEquals(snapshot.reviveAt(), reconnected.reviveAt());
        assertEquals(1, reconnected.deaths());
    }

    @Test
    void disconnectWithoutGhostTransitionPreservesAliveState() {
        UUID instance = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        PlayerLifecycleService service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC),
                ignored -> { });
        assertTrue(updates.register(instance, ignored -> { }));
        assertTrue(service.register(instance, List.of(player)).successful());
        assertTrue(service.start(instance).successful());

        assertTrue(service.disconnect(instance, player, false).successful());

        var snapshot = service.player(instance, player).orElseThrow();
        assertEquals(PlayerLifecycleService.PlayerState.ALIVE, snapshot.state());
        assertFalse(snapshot.online());
        assertEquals(0, snapshot.deaths());
        assertNull(snapshot.reviveAt());
    }

    @Test
    void escapedPlayerIsRemovedAndCannotBeRevivedLater() {
        UUID instance = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        PlayerLifecycleService service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC),
                ignored -> { });
        assertTrue(updates.register(instance, ignored -> { }));
        assertTrue(service.register(instance, List.of(player)).successful());
        assertTrue(service.start(instance).successful());

        assertTrue(service.escape(instance, player).successful());
        assertEquals(PlayerLifecycleService.PlayerState.REMOVED, service.player(instance, player).orElseThrow().state());
        assertFalse(service.revive(instance, player).successful());
        assertFalse(service.reconnect(instance, player).successful());
    }

    @Test
    void administrativeReviveUsesShortCountdownBeforeReviving() {
        UUID instance = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        List<PlayerLifecycleService.Notice> notices = new ArrayList<>();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        PlayerLifecycleService service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC),
                notices::add);
        assertTrue(updates.register(instance, ignored -> { }));
        assertTrue(service.register(instance, List.of(ghost, alive)).successful());
        assertTrue(service.start(instance).successful());
        assertTrue(service.lethal(instance, ghost, START).successful());

        var scheduled = service.scheduleAdminRevive(instance, ghost);

        assertTrue(scheduled.successful());
        assertEquals(PlayerLifecycleService.PlayerState.GHOST, service.player(instance, ghost).orElseThrow().state());
        assertEquals(START.plusSeconds(3), service.player(instance, ghost).orElseThrow().reviveAt());
        assertEquals(0, notices.stream().filter(value -> value.event() == PlayerLifecycleService.Event.REVIVED).count());

        updates.tick(START.plusSeconds(2));
        assertEquals(PlayerLifecycleService.PlayerState.GHOST, service.player(instance, ghost).orElseThrow().state());
        updates.tick(START.plusSeconds(3));
        assertEquals(PlayerLifecycleService.PlayerState.ALIVE, service.player(instance, ghost).orElseThrow().state());
        assertEquals(alive, service.player(instance, ghost).orElseThrow().reviveTarget());
    }

    @Test
    void simultaneousLethalTransitionsWipeExactlyOnce() throws Exception {
        UUID instance = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        List<PlayerLifecycleService.Notice> notices = Collections.synchronizedList(new ArrayList<>());
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        PlayerLifecycleService service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC),
                notices::add);
        assertTrue(updates.register(instance, ignored -> { }));
        assertTrue(service.register(instance, List.of(firstPlayer, secondPlayer)).successful());
        assertTrue(service.start(instance).successful());
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                start.await();
                return service.lethal(instance, firstPlayer, START);
            });
            var second = executor.submit(() -> {
                start.await();
                return service.lethal(instance, secondPlayer, START);
            });
            start.countDown();
            var results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertTrue(results.stream().allMatch(PlayerLifecycleService.TransitionResult::successful));
        }

        assertTrue(service.info(instance).orElseThrow().wiped());
        assertEquals(1, notices.stream().filter(value -> value.event() == PlayerLifecycleService.Event.WIPED).count());
        assertEquals(PlayerLifecycleService.PlayerState.GHOST,
                service.player(instance, firstPlayer).orElseThrow().state());
        assertEquals(PlayerLifecycleService.PlayerState.GHOST,
                service.player(instance, secondPlayer).orElseThrow().state());
        assertEquals(1, service.player(instance, firstPlayer).orElseThrow().deaths());
        assertEquals(1, service.player(instance, secondPlayer).orElseThrow().deaths());
    }

    @Test
    void simultaneousLogoutThenRejoinCannotResurrectWipedRun() throws Exception {
        UUID instance = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        List<PlayerLifecycleService.Notice> notices = Collections.synchronizedList(new ArrayList<>());
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        PlayerLifecycleService service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC),
                notices::add);
        assertTrue(updates.register(instance, ignored -> { }));
        assertTrue(service.register(instance, List.of(firstPlayer, secondPlayer)).successful());
        assertTrue(service.start(instance).successful());
        CountDownLatch disconnectStart = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                disconnectStart.await();
                return service.disconnect(instance, firstPlayer);
            });
            var second = executor.submit(() -> {
                disconnectStart.await();
                return service.disconnect(instance, secondPlayer);
            });
            disconnectStart.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).successful());
            assertTrue(second.get(5, TimeUnit.SECONDS).successful());
        }

        assertTrue(service.info(instance).orElseThrow().wiped());
        CountDownLatch reconnectStart = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                reconnectStart.await();
                return service.reconnect(instance, firstPlayer);
            });
            var second = executor.submit(() -> {
                reconnectStart.await();
                return service.reconnect(instance, secondPlayer);
            });
            reconnectStart.countDown();
            assertFalse(first.get(5, TimeUnit.SECONDS).successful());
            assertFalse(second.get(5, TimeUnit.SECONDS).successful());
        }

        assertEquals(1, notices.stream().filter(value -> value.event() == PlayerLifecycleService.Event.WIPED).count());
        assertFalse(service.player(instance, firstPlayer).orElseThrow().online());
        assertFalse(service.player(instance, secondPlayer).orElseThrow().online());
    }

    @Test
    void disableFreezePreventsAQueuedGhostTickFromReviving() {
        UUID instance = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        PlayerLifecycleService service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC),
                ignored -> { });
        assertTrue(updates.register(instance, ignored -> { }));
        assertTrue(service.register(instance, List.of(ghost, alive)).successful());
        assertTrue(service.start(instance).successful());
        service.lethal(instance, ghost, START);

        service.freezeForDisable();
        updates.tick(START.plusSeconds(60));

        assertEquals(PlayerLifecycleService.PlayerState.GHOST,
                service.player(instance, ghost).orElseThrow().state());
    }
    @Test
    void runicReviveIsLatchedAtDeathKeepsSoloAliveAndRetainsItsDeath() {
        UUID instance = UUID.randomUUID(), player = UUID.randomUUID();
        var active = new java.util.concurrent.atomic.AtomicBoolean(true);
        var effect = new java.util.concurrent.atomic.AtomicBoolean(false);
        var updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        var service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        service.configureRevival(id -> active.get(), notice -> { assertNull(notice.reviveTarget()); return effect.get(); });
        updates.register(instance, ignored -> { }); service.register(instance, List.of(player));
        service.configureOrdinaryRevival(instance, false); service.start(instance);
        service.lethal(instance, player, START);
        active.set(false);
        assertFalse(service.info(instance).orElseThrow().wiped());
        updates.tick(START.plusSeconds(5));
        assertFalse(service.player(instance, player).orElseThrow().runicChargeUsed());
        assertEquals(1, service.player(instance, player).orElseThrow().deaths());
        effect.set(true); updates.tick(START.plusSeconds(6));
        assertTrue(service.player(instance, player).orElseThrow().runicChargeUsed());
        assertEquals(1, service.player(instance, player).orElseThrow().deaths());
        active.set(true); service.lethal(instance, player, START.plusSeconds(10));
        assertTrue(service.info(instance).orElseThrow().wiped());
        assertEquals(2, service.player(instance, player).orElseThrow().deaths());
    }

    @Test
    void hellishGhostHasNoTimerAndEquippingPetAfterDeathDoesNotCreateOne() {
        UUID instance = UUID.randomUUID(), player = UUID.randomUUID(), alive = UUID.randomUUID();
        var active = new java.util.concurrent.atomic.AtomicBoolean(false);
        var updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        var service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        service.configureRevival(id -> active.get(), notice -> true);
        updates.register(instance, ignored -> { }); service.register(instance, List.of(player, alive));
        service.configureOrdinaryRevival(instance, false); service.start(instance);
        service.lethal(instance, player, START); active.set(true);
        updates.tick(START.plusSeconds(500));
        assertNull(service.player(instance, player).orElseThrow().reviveAt());
        assertEquals(PlayerLifecycleService.PlayerState.GHOST, service.player(instance, player).orElseThrow().state());
        assertFalse(service.revive(instance, player).successful());
    }

    @Test
    void completionRevivesOnlineGhostsAndOfflineGhostsOnReconnectRetainingDeaths() {
        UUID instance = UUID.randomUUID(), ghost = UUID.randomUUID(), offline = UUID.randomUUID();
        UUID alive = UUID.randomUUID(), removed = UUID.randomUUID();
        var notices = new ArrayList<PlayerLifecycleService.Notice>();
        var updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        var service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC), notices::add);
        var effects = new ArrayList<UUID>();
        service.configureRevival(id -> false, notice -> {
            assertNull(notice.reviveTarget());
            effects.add(notice.playerId());
            return true;
        });
        updates.register(instance, ignored -> { });
        service.register(instance, List.of(ghost, offline, alive, removed));
        service.configureOrdinaryRevival(instance, false);
        service.start(instance);
        service.lethal(instance, ghost, START);
        service.disconnect(instance, offline);
        service.escape(instance, removed);
        assertTrue(service.complete(instance).successful());
        assertEquals(List.of(ghost), effects);
        var revived = service.player(instance, ghost).orElseThrow();
        assertEquals(PlayerLifecycleService.PlayerState.ALIVE, revived.state());
        assertEquals(1, revived.deaths());
        assertFalse(revived.runicChargeUsed());
        assertNull(revived.reviveAt());
        assertFalse(service.info(instance).orElseThrow().running());
        assertTrue(service.complete(instance).successful());
        assertEquals(1, effects.size());
        assertTrue(service.reconnect(instance, offline).successful());
        assertEquals(PlayerLifecycleService.PlayerState.ALIVE, service.player(instance, offline).orElseThrow().state());
        assertEquals(1, service.player(instance, offline).orElseThrow().deaths());
        assertEquals(PlayerLifecycleService.PlayerState.REMOVED, service.player(instance, removed).orElseThrow().state());
        updates.tick(START.plusSeconds(100));
        assertEquals(2, notices.stream().filter(notice -> notice.event() == PlayerLifecycleService.Event.REVIVED).count());
        assertFalse(service.start(instance).successful());
    }

    @Test
    void failedCompletionRevivalRemainsRetryableAndCancelsGhostTimer() {
        UUID instance = UUID.randomUUID(), ghost = UUID.randomUUID(), alive = UUID.randomUUID();
        var effect = new java.util.concurrent.atomic.AtomicBoolean(false);
        var updates = new CentralUpdateService(Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        var service = new PlayerLifecycleService(updates, Clock.fixed(START, ZoneOffset.UTC), ignored -> { });
        service.configureRevival(id -> false, notice -> effect.get());
        updates.register(instance, ignored -> { });
        service.register(instance, List.of(ghost, alive));
        service.start(instance);
        service.lethal(instance, ghost, START);
        assertFalse(service.complete(instance).successful());
        assertEquals(PlayerLifecycleService.PlayerState.GHOST, service.player(instance, ghost).orElseThrow().state());
        assertNull(service.player(instance, ghost).orElseThrow().reviveAt());
        effect.set(true);
        assertTrue(service.complete(instance).successful());
        assertEquals(PlayerLifecycleService.PlayerState.ALIVE, service.player(instance, ghost).orElseThrow().state());
        assertEquals(1, service.player(instance, ghost).orElseThrow().deaths());
    }

}
