package me.lidan.dungeonCrawlers.core.reward;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RewardDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RewardItem;
import me.lidan.dungeonCrawlers.core.score.DungeonRank;
import me.lidan.dungeonCrawlers.core.score.ScoreService;
import me.lidan.dungeonCrawlers.persistence.FileDurableRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardEntitlementServiceTest {
    private static final Instant COMPLETED = Instant.parse("2026-08-22T00:00:00Z");
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID ACTIVE = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID OFFLINE = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID REMOVED = UUID.fromString("00000000-0000-0000-0000-000000000023");
    private final RewardDefinition rewards = new RewardDefinition(true, 0, 0, 2, false, List.of(
            new RewardItem("a", 1, 1, 8), new RewardItem("b", 1, 1, 8),
            new RewardItem("c", 1, 1, 8), new RewardItem("d", 1, 1, 8)));

    @Test
    void removedPlayersGetNothingAndOfflineActivePlayersRecoverWithoutRerolling() {
        RewardEntitlementService service = service(Set.of("a", "b", "c", "d"));
        service.register(completion(List.of(
                new RewardEntitlementService.Participant(ACTIVE, true, true),
                new RewardEntitlementService.Participant(OFFLINE, true, false),
                new RewardEntitlementService.Participant(REMOVED, false, false))));

        assertTrue(service.player(INSTANCE, REMOVED).isEmpty());
        RewardEntitlementService.PlayerEntitlement recovered = service.open(INSTANCE, OFFLINE).orElseThrow();
        assertEquals(me.lidan.dungeonCrawlers.core.claim.OfferMode.RECOVERED, recovered.mode());
        assertTrue(recovered.sessionExpiresAt().isAfter(recovered.sessionStartedAt()));
        var first = service.preview(INSTANCE, OFFLINE, "wooden").orElseThrow();
        var reopened = service.open(INSTANCE, OFFLINE).orElseThrow();
        assertEquals(first, reopened.offers().get("wooden"));

        var activeOffer = service.preview(INSTANCE, ACTIVE, "wooden").orElseThrow();
        assertNotEquals(activeOffer.offerId(), first.offerId());
        assertFalse(activeOffer.rolls().isEmpty());
    }

    @Test
    void disabledUnconfiguredAndScoreLockedOffersAreDistinguished() {
        RewardEntitlementService service = service(Set.of("a"));
        RewardDefinition disabled = new RewardDefinition(false, 0, 0, 1, false,
                List.of(new RewardItem("a", 1, 1, 1)));
        RewardDefinition missing = new RewardDefinition(true, 0, 0, 1, false,
                List.of(new RewardItem("missing", 1, 1, 1)));
        RewardDefinition locked = new RewardDefinition(true, 25, 301, 1, false,
                List.of(new RewardItem("a", 1, 1, 1)));
        service.register(new RewardEntitlementService.Completion(INSTANCE, 42, COMPLETED, score(300),
                List.of(new RewardEntitlementService.Participant(ACTIVE, true, true)),
                Map.of("disabled", disabled, "missing", missing, "locked", locked)));

        var player = service.player(INSTANCE, ACTIVE).orElseThrow();
        assertFalse(player.offers().containsKey("disabled"));
        assertFalse(player.offers().containsKey("missing"));
        assertTrue(player.offers().get("locked").locked());
        assertEquals(301, player.offers().get("locked").minScore());
        assertTrue(player.offers().get("locked").rolls().isEmpty());
    }

    @Test
    void registrationIsIdempotentAndRecoveredWindowExpires() {
        Clock clock = Clock.fixed(COMPLETED.plus(Duration.ofHours(25)), ZoneOffset.UTC);
        RewardEntitlementService service = new RewardEntitlementService(clock, Set.of("a")::contains);
        var completion = completion(List.of(new RewardEntitlementService.Participant(OFFLINE, true, false)));
        var first = service.register(completion);
        var second = service.register(completion);
        assertEquals(first, second);
        assertTrue(service.open(INSTANCE, OFFLINE).isEmpty());
    }

    @Test
    void unsuccessfulCompletionDoesNotEntitlePlayers() {
        RewardEntitlementService service = service(Set.of("a", "b", "c", "d"));
        ScoreService.ScoreResult failure = new ScoreService.ScoreResult(0, 100, 100, 0, 200,
                DungeonRank.B, List.of());
        ScoreService.FinalScoreSnapshot failedScore = new ScoreService.FinalScoreSnapshot(
                new ScoreService.ScoreInput(false, 0, Duration.ofMinutes(8), 0, 0),
                0, 100, 100, 0, 200, failure.rank(), failure.bonusFacts());
        service.register(new RewardEntitlementService.Completion(INSTANCE, 42, COMPLETED, failedScore,
                List.of(new RewardEntitlementService.Participant(ACTIVE, true, true)),
                Map.of("wooden", rewards)));
        assertTrue(service.player(INSTANCE, ACTIVE).isEmpty());
    }

    @Test
    void independentPlayerStreamsAreVerifiedByTheirStableKeys() {
        assertEquals("reward:" + ACTIVE + ":wooden",
                RewardEntitlementService.rewardStreamKey(ACTIVE, "wooden"));
        assertEquals("reward:" + OFFLINE + ":wooden",
                RewardEntitlementService.rewardStreamKey(OFFLINE, "wooden"));
        assertNotEquals(RewardEntitlementService.rewardStreamKey(ACTIVE, "wooden"),
                RewardEntitlementService.rewardStreamKey(OFFLINE, "wooden"));
    }

    @Test
    void recoveredSessionSurvivesServiceRestart(@TempDir Path directory) {
        Clock clock = Clock.fixed(COMPLETED.plusSeconds(10), ZoneOffset.UTC);
        FileDurableRepository firstRepository = new FileDurableRepository(directory, 10, Runnable::run);
        RewardEntitlementService first = new RewardEntitlementService(clock, Set.of("a", "b", "c", "d")::contains,
                firstRepository);
        first.register(completion(List.of(new RewardEntitlementService.Participant(OFFLINE, true, false))));
        var opened = first.open(INSTANCE, OFFLINE).orElseThrow();
        firstRepository.close();

        FileDurableRepository secondRepository = new FileDurableRepository(directory, 10, Runnable::run);
        RewardEntitlementService restored = new RewardEntitlementService(clock,
                Set.of("a", "b", "c", "d")::contains, secondRepository);

        var restoredPlayer = restored.player(INSTANCE, OFFLINE).orElseThrow();
        assertEquals(opened.sessionStartedAt(), restoredPlayer.sessionStartedAt());
        assertEquals(opened.sessionExpiresAt(), restoredPlayer.sessionExpiresAt());
        assertEquals(opened.offers(), restoredPlayer.offers());
        assertTrue(restored.open(INSTANCE, OFFLINE).isPresent());
        secondRepository.close();
    }

    private RewardEntitlementService service(Set<String> configured) {
        return new RewardEntitlementService(Clock.fixed(COMPLETED.plusSeconds(10), ZoneOffset.UTC),
                configured::contains);
    }

    private RewardEntitlementService.Completion completion(List<RewardEntitlementService.Participant> participants) {
        return new RewardEntitlementService.Completion(INSTANCE, 42, COMPLETED, score(300), participants,
                Map.of("wooden", rewards));
    }

    private static ScoreService.FinalScoreSnapshot score(int total) {
        ScoreService.ScoreResult result = new ScoreService.ScoreResult(100, 100, 100, 0, total,
                DungeonRank.forScore(total), List.of());
        return new ScoreService.FinalScoreSnapshot(
                new ScoreService.ScoreInput(true, 0, Duration.ofMinutes(8), 0, 0),
                result.skill(), result.time(), result.exploration(), result.bonus(), result.total(),
                result.rank(), result.bonusFacts());
    }
}
