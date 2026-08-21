package me.lidan.dungeonCrawlers.core.lifecycle;

import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
