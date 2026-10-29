package me.lidan.dungeonCrawlers.core.encounter;

import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Attack;
import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Stage;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChainboundEncounterTest {
    private static final Instant START = Instant.parse("2026-10-10T00:00:00Z");
    private final BossEntityGateway entities = mock(BossEntityGateway.class);
    private final ChainboundEncounter.Arena arena = mock(ChainboundEncounter.Arena.class);
    private final UUID boss = UUID.randomUUID();
    private final UUID secondBoss = UUID.randomUUID();
    private final Clock clock = mock(Clock.class);
    private final ChainboundEncounter.Settings settings = ChainboundEncounter.Settings.defaults();

    private ChainboundEncounter encounter(boolean impossible) {
        when(entities.spawn(any(), any(), any())).thenReturn(BossEntityGateway.SpawnResult.success(boss, "spawned"));
        when(entities.isValid(boss)).thenReturn(true);
        when(arena.healthFraction()).thenReturn(1D);
        when(clock.instant()).thenReturn(START);
        return new ChainboundEncounter(new EncounterFactory.EncounterContext(UUID.randomUUID(), "chainbound",
                "VeyraChainbound", new Point(40, 76, 40), entities, ignored -> { }), arena, settings,
                impossible, clock);
    }
    private void defeatFirst(ChainboundEncounter encounter) {
        when(clock.instant()).thenReturn(START.plusSeconds(8));
        when(entities.spawn(any(), any(), any())).thenReturn(BossEntityGateway.SpawnResult.success(secondBoss, "second life"));
        when(entities.isValid(boss)).thenReturn(false);
        when(entities.isValid(secondBoss)).thenReturn(true);
        when(arena.healthFraction()).thenReturn(1D);
        assertTrue(encounter.onDeath(boss).accepted());
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
    @Test void impossibleFirstDefeatTransformsExactlyOnceWithAFullSecondLife() {
        var encounter = encounter(true); encounter.start(); encounter.tick(START.plusSeconds(7));
        defeatFirst(encounter);
        assertEquals(Stage.TRANSFORM, encounter.stage());
        encounter.tick(START.plusSeconds(21));
        assertEquals(Stage.TRANSFORM, encounter.stage());
        encounter.tick(START.plusSeconds(22));
        assertEquals(Stage.RIVEN, encounter.stage());
        encounter.tick(START.plusSeconds(24));
        verify(arena).cast(Attack.CHAIN_DRAW, START.plusSeconds(24));
        encounter.tick(START.plusSeconds(40));
        verify(arena, times(1)).stage(eq(Stage.TRANSFORM), any());
        verify(entities, times(2)).spawn(any(), any(), any());
        verify(arena).begin(secondBoss, settings);
        assertEquals(secondBoss, encounter.entityId().orElseThrow());
        assertFalse(encounter.onDeath(boss).accepted());
        assertFalse(encounter.complete());
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
    @Test void nearLethalFirstLifeDoesNotTransformOrSkipToFinalThenDeathStartsPhaseTwo() {
        var encounter = encounter(true); encounter.start(); encounter.tick(START.plusSeconds(7));
        when(arena.healthFraction()).thenReturn(.05);
        encounter.tick(START.plusSeconds(8));
        assertEquals(Stage.FIRST, encounter.stage());
        defeatFirst(encounter);
        assertEquals(Stage.TRANSFORM, encounter.stage());
        encounter.tick(START.plusSeconds(22));
        assertEquals(Stage.RIVEN, encounter.stage());
        verify(arena, never()).stage(eq(Stage.FINAL), any());
        when(arena.healthFraction()).thenReturn(.05);
        encounter.tick(START.plusSeconds(23));
        assertEquals(Stage.FINAL, encounter.stage());
        var order = inOrder(arena);
        order.verify(arena).stage(eq(Stage.TRANSFORM), any());
        order.verify(arena).stage(eq(Stage.RIVEN), any());
        order.verify(arena).stage(eq(Stage.FINAL), any());
        encounter.tick(START.plusSeconds(25)); encounter.tick(START.plusSeconds(31));
        encounter.tick(START.plusSeconds(36)); encounter.tick(START.plusSeconds(42));
        verify(arena).cast(eq(Attack.LAST_WEAVE), any());
    }
    @Test void rivenAndFinalCyclesIncludeCounterplayAndNeverSkipTheSignatureOpener() {
        var encounter = encounter(true); encounter.start(); encounter.tick(START.plusSeconds(7));
        defeatFirst(encounter); encounter.tick(START.plusSeconds(22));
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
        defeatFirst(encounter);
        encounter.cleanup(); encounter.cleanup();
        assertFalse(encounter.tick(START.plusSeconds(30)).successful());
        assertFalse(encounter.complete());
        verify(arena).cleanup(); verify(entities).remove(secondBoss);
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
                Double.NaN, 1, .18, 6500, 14000, 2000, 8500, 6500, 5500, 5000));
        assertThrows(IllegalArgumentException.class, () -> new ChainboundEncounter.Settings(
                1, 1, 1, 6500, 14000, 2000, 8500, 6500, 5500, 5000));
        assertThrows(IllegalArgumentException.class, () -> new ChainboundEncounter.Settings(
                1, 1, .18, 6500, 14000, 2000, 8500, 6500, 4000, 5000));
    }
    @Test void firstLifeKilledDuringIntroStillStartsTheFullCinematic() {
        var encounter = encounter(true); encounter.start(); defeatFirst(encounter);
        assertFalse(encounter.tick(START.plusMillis(21999)).completed());
        assertEquals(Stage.TRANSFORM, encounter.stage());
        verify(arena, never()).cast(any(), any());
        encounter.tick(START.plusSeconds(22));
        assertEquals(Stage.RIVEN, encounter.stage());
    }
    @Test void onlySecondDefeatRunsVictoryAndDuplicateDeathsCannotPayTwice() {
        var encounter = encounter(true); encounter.start(); defeatFirst(encounter);
        encounter.tick(START.plusSeconds(22));
        when(clock.instant()).thenReturn(START.plusSeconds(23));
        assertTrue(encounter.onDeath(secondBoss).accepted());
        assertEquals(Stage.DYING, encounter.stage());
        assertFalse(encounter.onDeath(secondBoss).accepted());
        assertFalse(encounter.onDeath(boss).accepted());
        assertFalse(encounter.tick(START.plusMillis(27999)).completed());
        assertTrue(encounter.tick(START.plusSeconds(28)).completed());
        verify(arena).cleanup();
        verify(entities, times(2)).spawn(any(), any(), any());
    }
    @Test void failedSecondSpawnOrAnimationFailsWithoutVictoryAndCleansOwnedState() {
        var encounter = encounter(true); encounter.start();
        when(entities.spawn(any(), any(), any())).thenReturn(BossEntityGateway.SpawnResult.failure("spawn refused"));
        assertTrue(encounter.onDeath(boss).accepted());
        assertEquals(Stage.FAILED, encounter.stage());
        assertFalse(encounter.tick(START.plusSeconds(30)).completed());
        verify(arena).cleanup();
        clearInvocations(arena, entities);
        var second = encounter(true); second.start();
        doThrow(new IllegalStateException("scene failure")).when(arena).stage(eq(Stage.TRANSFORM), any());
        defeatFirst(second);
        assertEquals(Stage.FAILED, second.stage());
        verify(entities).remove(secondBoss); verify(arena).cleanup();
    }
    @Test void forcedSecondDeathDuringProtectedCinematicFailsWithoutRewardOrRespawnLoop() {
        var encounter = encounter(true); encounter.start(); defeatFirst(encounter);
        assertTrue(encounter.onDeath(secondBoss).accepted());
        assertEquals(Stage.FAILED, encounter.stage());
        assertFalse(encounter.complete());
        verify(arena).cleanup(); verify(entities, times(2)).spawn(any(), any(), any());
    }
}
