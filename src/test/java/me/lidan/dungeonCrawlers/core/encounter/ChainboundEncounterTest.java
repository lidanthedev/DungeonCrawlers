package me.lidan.dungeonCrawlers.core.encounter;

import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Attack;
import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Stage;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChainboundEncounterTest {
    private static final Instant START = Instant.parse("2026-10-10T00:00:00Z");
    private final BossEntityGateway entities = mock(BossEntityGateway.class);
    private final ChainboundEncounter.Arena arena = mock(ChainboundEncounter.Arena.class);
    private final UUID boss = UUID.randomUUID();
    private final ChainboundEncounter.Settings settings = ChainboundEncounter.Settings.defaults();

    private ChainboundEncounter encounter(boolean impossible) {
        when(entities.spawn(any(), any(), any())).thenReturn(BossEntityGateway.SpawnResult.success(boss, "spawned"));
        when(entities.isValid(boss)).thenReturn(true);
        when(arena.healthFraction()).thenReturn(1D);
        return new ChainboundEncounter(new EncounterFactory.EncounterContext(UUID.randomUUID(), "chainbound",
                "VeyraChainbound", new Point(40, 76, 40), entities, ignored -> { }), arena, settings,
                impossible, Clock.fixed(START, ZoneOffset.UTC));
    }
    @Test void introHasNoAttacksAndThenStartsFirstPattern() {
        var encounter = encounter(true);
        assertTrue(encounter.start().successful());
        encounter.tick(START.plusMillis(6499));
        verify(arena, never()).cast(any(), any());
        encounter.tick(START.plusMillis(6500));
        assertEquals(Stage.FIRST, encounter.stage());
        encounter.tick(START.plusMillis(8500));
        verify(arena).cast(Attack.FORGE_WAVE, START.plusMillis(8500));
    }
    @Test void impossibleTransformsExactlyOnceWithoutRespawningOrLosingHealthPool() {
        var encounter = encounter(true); encounter.start(); encounter.tick(START.plusSeconds(7));
        when(arena.healthFraction()).thenReturn(.65);
        encounter.tick(START.plusSeconds(8));
        assertEquals(Stage.TRANSFORM, encounter.stage());
        encounter.tick(START.plusSeconds(21));
        assertEquals(Stage.TRANSFORM, encounter.stage());
        encounter.tick(START.plusSeconds(22));
        assertEquals(Stage.RIVEN, encounter.stage());
        encounter.tick(START.plusSeconds(24));
        verify(arena).cast(Attack.CHAIN_DRAW, START.plusSeconds(24));
        encounter.tick(START.plusSeconds(40));
        verify(arena, times(1)).stage(eq(Stage.TRANSFORM), any());
        verify(entities, times(1)).spawn(any(), any(), any());
        assertEquals(boss, encounter.entityId().orElseThrow());
    }
    @Test void normalNeverTransformsEvenBelowBothThresholds() {
        var encounter = encounter(false); encounter.start(); encounter.tick(START.plusSeconds(7));
        when(arena.healthFraction()).thenReturn(.1);
        encounter.tick(START.plusSeconds(8)); encounter.tick(START.plusSeconds(10));
        assertEquals(Stage.FINAL, encounter.stage());
        verify(arena, never()).stage(eq(Stage.TRANSFORM), any());
        verify(arena).cast(eq(Attack.FORGE_WAVE), any());
        verify(arena, never()).cast(eq(Attack.CHAIN_DRAW), any());
    }
    @Test void burstAcrossBothThresholdsStillRevealsArenaBeforeFinalEnrage() {
        var encounter = encounter(true); encounter.start(); encounter.tick(START.plusSeconds(7));
        when(arena.healthFraction()).thenReturn(.05);
        encounter.tick(START.plusSeconds(8)); encounter.tick(START.plusSeconds(22));
        assertEquals(Stage.FINAL, encounter.stage());
        var order = inOrder(arena);
        order.verify(arena).stage(eq(Stage.TRANSFORM), any());
        order.verify(arena).stage(eq(Stage.RIVEN), any());
        order.verify(arena).stage(eq(Stage.FINAL), any());
        encounter.tick(START.plusSeconds(24)); encounter.tick(START.plusSeconds(30));
        encounter.tick(START.plusSeconds(36)); encounter.tick(START.plusSeconds(42));
        verify(arena).cast(eq(Attack.LAST_WEAVE), any());
    }
    @Test void rivenAndFinalCyclesIncludeCounterplayAndNeverSkipTheSignatureOpener() {
        var encounter = encounter(true); encounter.start(); encounter.tick(START.plusSeconds(7));
        when(arena.healthFraction()).thenReturn(.6);
        encounter.tick(START.plusSeconds(8)); encounter.tick(START.plusSeconds(22));
        clearInvocations(arena);
        for (int i = 0; i < 7; i++) encounter.tick(START.plusMillis(24000 + i * 6500));
        var order = inOrder(arena);
        for (Attack attack : new Attack[]{Attack.CHAIN_DRAW, Attack.GUILLOTINE, Attack.RIFT_PULSE,
                Attack.CHAIN_CAGE, Attack.CLOCKWORK_REQUIEM, Attack.COUNTERWEIGHTS, Attack.BRANDS})
            order.verify(arena).cast(eq(attack), any());
        when(arena.healthFraction()).thenReturn(.17);
        encounter.tick(START.plusSeconds(65));
        encounter.tick(START.plusSeconds(67));
        verify(arena).cast(eq(Attack.LAST_WEAVE), eq(START.plusSeconds(67)));
    }
    @Test void ordinaryFloorTeachesCounterweightsWithoutImpossibleChains() {
        var encounter = encounter(false); encounter.start(); encounter.tick(START.plusMillis(6500));
        for (int i = 0; i < 4; i++) encounter.tick(START.plusMillis(8500 + i * 8500));
        verify(arena).cast(eq(Attack.COUNTERWEIGHTS), any());
        verify(arena, never()).cast(eq(Attack.CHAIN_CAGE), any());
        verify(arena, never()).cast(eq(Attack.CLOCKWORK_REQUIEM), any());
    }
    @Test void exactDeathWaitsForVictoryAndCannotPayTwice() {
        var encounter = encounter(false); encounter.start();
        assertFalse(encounter.onDeath(UUID.randomUUID()).accepted());
        assertTrue(encounter.onDeath(boss).accepted());
        assertFalse(encounter.complete());
        assertFalse(encounter.onDeath(boss).accepted());
        when(entities.isValid(boss)).thenReturn(false);
        assertFalse(encounter.tick(START.plusMillis(4999)).completed());
        assertTrue(encounter.tick(START.plusMillis(5000)).completed());
        assertTrue(encounter.tick(START.plusSeconds(6)).completed());
        verify(arena).cleanup();
    }
    @Test void wipeDuringTransformationCancelsAnimationsAndFutureAttacks() {
        var encounter = encounter(true); encounter.start(); encounter.tick(START.plusSeconds(7));
        when(arena.healthFraction()).thenReturn(.6); encounter.tick(START.plusSeconds(8));
        encounter.cleanup(); encounter.cleanup();
        assertFalse(encounter.tick(START.plusSeconds(30)).successful());
        assertFalse(encounter.complete());
        verify(arena).cleanup(); verify(entities).remove(boss);
        verify(arena, never()).stage(eq(Stage.RIVEN), any());
    }
    @Test void entityDisappearanceFailsWithoutRewardAndCleansTheArena() {
        var encounter = encounter(true); encounter.start(); when(entities.isValid(boss)).thenReturn(false);
        assertFalse(encounter.tick(START.plusSeconds(7)).successful());
        assertEquals(Stage.FAILED, encounter.stage()); assertFalse(encounter.complete());
        verify(arena).cleanup();
    }
    @Test void failedSpawnAndFailedAnimationBothCleanUpWithoutRetry() {
        var encounter = encounter(true);
        when(entities.spawn(any(), any(), any())).thenReturn(BossEntityGateway.SpawnResult.failure("missing actor"));
        assertFalse(encounter.start().successful()); assertFalse(encounter.start().successful());
        verify(arena).cleanup();
        clearInvocations(arena, entities);
        var second = encounter(true); second.start();
        doThrow(new IllegalStateException("display failure")).when(arena).tick(any());
        assertFalse(second.tick(START.plusSeconds(7)).successful());
        verify(entities).remove(boss); verify(arena).cleanup();
    }
    @Test void invalidSettingsRejectUnsafeThresholdsAndOverlappingAttackSchedules() {
        assertThrows(IllegalArgumentException.class, () -> new ChainboundEncounter.Settings(
                Double.NaN, 1, .65, .18, 6500, 14000, 2000, 8500, 6500, 5500, 5000));
        assertThrows(IllegalArgumentException.class, () -> new ChainboundEncounter.Settings(
                1, 1, .18, .65, 6500, 14000, 2000, 8500, 6500, 5500, 5000));
        assertThrows(IllegalArgumentException.class, () -> new ChainboundEncounter.Settings(
                1, 1, .65, .18, 6500, 14000, 2000, 8500, 6500, 4000, 5000));
    }
}
