package me.lidan.dungeonCrawlers.core.encounter;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RingmasterEncounterTest {
    private static final Instant START = Instant.parse("2026-10-09T00:00:00Z");
    private final BossEntityGateway entities = mock(BossEntityGateway.class);
    private final RingmasterEncounter.Arena arena = mock(RingmasterEncounter.Arena.class);
    private final UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    private final RingmasterEncounter encounter = new RingmasterEncounter(new EncounterFactory.EncounterContext(
            UUID.randomUUID(), "ringmaster", "MadRingmaster", new Point(10, 64, 0), entities, ignored -> { }),
            arena, Clock.fixed(START, ZoneOffset.UTC));

    private void spawnBoth() {
        when(entities.spawn(any(), eq("MadRingmaster"), any())).thenReturn(BossEntityGateway.SpawnResult.success(first, "first"));
        when(entities.spawn(any(), eq("MadRingmasterEncore"), any())).thenReturn(BossEntityGateway.SpawnResult.success(second, "second"));
        when(entities.isValid(any())).thenReturn(true);
        when(arena.healthFraction()).thenReturn(1D);
    }

    @Test
    void fakeDeathWaitsSixSecondsAndOnlyExactSecondDeathCompletes() {
        spawnBoth();
        assertTrue(encounter.start().successful());
        assertFalse(encounter.onDeath(UUID.randomUUID()).accepted());
        assertFalse(encounter.onDeath(first).completed());
        assertFalse(encounter.complete());
        assertFalse(encounter.onDeath(first).accepted());
        assertTrue(encounter.entityId().isEmpty());
        encounter.tick(START.plusSeconds(3));
        verify(entities, never()).spawn(any(), eq("MadRingmasterEncore"), any());
        encounter.tick(START.plusSeconds(6));
        assertEquals(second, encounter.entityId().orElseThrow());
        assertFalse(encounter.onDeath(first).accepted());
        assertTrue(encounter.onDeath(second).completed());
        assertTrue(encounter.complete());
        assertFalse(encounter.onDeath(second).accepted());
        assertTrue(encounter.tick(START.plusSeconds(7)).completed());
        verify(arena, times(2)).clear();
        verify(arena).transition(START);
    }

    @Test
    void failedEncoreFailsWithoutCompletionAndNeverRetries() {
        spawnBoth();
        when(entities.spawn(any(), eq("MadRingmasterEncore"), any())).thenReturn(BossEntityGateway.SpawnResult.failure("missing mob"));
        encounter.start(); encounter.onDeath(first);
        assertFalse(encounter.tick(START.plusSeconds(6)).successful());
        assertFalse(encounter.tick(START.plusSeconds(7)).successful());
        assertFalse(encounter.complete());
        verify(entities, times(1)).spawn(any(), eq("MadRingmasterEncore"), any());
        verify(arena).cleanup();
    }

    @Test
    void cleanupDuringTransitionCancelsRevivalAndIsIdempotent() {
        spawnBoth(); encounter.start(); encounter.onDeath(first);
        encounter.cleanup(); encounter.cleanup();
        assertFalse(encounter.tick(START.plusSeconds(10)).successful());
        verify(entities, never()).spawn(any(), eq("MadRingmasterEncore"), any());
        verify(arena).cleanup();
    }

    @Test
    void missingBossAndAttackFailureCleanUpWithoutReward() {
        spawnBoth(); encounter.start();
        doThrow(new IllegalStateException("attack failed")).when(arena).tick(any());
        assertFalse(encounter.tick(START.plusSeconds(6)).successful());
        verify(entities).remove(first);
        verify(arena).cleanup();
        assertFalse(encounter.complete());
    }

    @Test
    void secondActHasOneFinaleAndNoPerformers() {
        spawnBoth(); encounter.start(); encounter.onDeath(first);
        encounter.tick(START.plusSeconds(6));
        when(arena.healthFraction()).thenReturn(.2);
        encounter.tick(START.plusSeconds(8));
        encounter.tick(START.plusSeconds(26));
        encounter.tick(START.plusSeconds(50));
        verify(arena, times(1)).cast(eq(RingmasterEncounter.Attack.SCYTHES), any());
        verify(arena, never()).summonPerformers();
    }

    @Test
    void introPreventsEarlyAttackAndFirstActSummonsOnAnInterval() {
        spawnBoth(); encounter.start();
        encounter.tick(START.plusSeconds(3));
        verify(arena, never()).cast(any(), any());
        encounter.tick(START.plusSeconds(6));
        encounter.tick(START.plusSeconds(10));
        encounter.tick(START.plusSeconds(21));
        verify(arena, times(2)).summonPerformers();
        verify(arena, atLeastOnce()).cast(eq(RingmasterEncounter.Attack.BALLOONS), any());
    }
    @Test
    void firstActCyclesBalloonsCardsAndBoxesThenEncoreStartsWithCarousel() {
        spawnBoth(); encounter.start();
        encounter.tick(START.plusSeconds(6));
        encounter.tick(START.plusSeconds(14));
        encounter.tick(START.plusSeconds(22));
        verify(arena).ready();
        verify(arena).cast(eq(RingmasterEncounter.Attack.BALLOONS), any());
        verify(arena).cast(eq(RingmasterEncounter.Attack.CARDS), any());
        verify(arena).cast(eq(RingmasterEncounter.Attack.JACK_IN_THE_BOX), any());
        encounter.onDeath(first);
        encounter.tick(START.plusSeconds(23));
        encounter.tick(START.plusSeconds(25));
        verify(arena).cast(eq(RingmasterEncounter.Attack.CAROUSEL), any());
    }

    @Test
    void carouselAllowsTheNextAttackAfterFourteenSeconds() {
        spawnBoth(); encounter.start(); encounter.onDeath(first);
        encounter.tick(START.plusSeconds(6));
        encounter.tick(START.plusSeconds(8));
        verify(arena).cast(eq(RingmasterEncounter.Attack.CAROUSEL), eq(START.plusSeconds(8)));
        clearInvocations(arena);
        encounter.tick(START.plusSeconds(21));
        verify(arena, never()).cast(any(), any());
        encounter.tick(START.plusSeconds(22));
        verify(arena).cast(eq(RingmasterEncounter.Attack.CARDS), eq(START.plusSeconds(22)));
    }
}
