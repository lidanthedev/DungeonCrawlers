package me.lidan.dungeonCrawlers.persistence.model;

import me.lidan.dungeonCrawlers.core.claim.ClaimGroup;
import me.lidan.dungeonCrawlers.core.claim.OfferMode;
import me.lidan.dungeonCrawlers.core.claim.OfferSnapshot;
import me.lidan.dungeonCrawlers.core.claim.OfferState;
import me.lidan.dungeonCrawlers.core.reward.RewardModels.ItemPayload;
import me.lidan.dungeonCrawlers.core.score.DungeonRank;
import me.lidan.dungeonCrawlers.core.score.ScoreService.ScoreResult;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletedRunRecordCodecTest {
    @Test
    void deterministicRoundTripPreservesExactOfferPayload() {
        Instant completedAt = Instant.parse("2026-01-01T00:00:00Z");
        UUID instance = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        ItemPayload item = new ItemPayload(UUID.randomUUID(), "crypt_fragment", 3,
                new byte[]{1, 2, 3}, "payload-checksum");
        OfferSnapshot offer = new OfferSnapshot(offerId, OfferMode.LIVE, OfferState.AVAILABLE, null,
                completedAt, null, null, null, null, completedAt, null, "Economy", player, 100,
                List.of(item));
        CompletedRunRecord original = new CompletedRunRecord(instance, "floor_1", 42, "config", "content",
                "v1", new ScoreResult(100, 100, 100, 0, 300, DungeonRank.S_PLUS, List.of()),
                Map.of(player, new CompletedRunRecord.ParticipantResult(player, 0, true)), Set.of(player),
                Map.of(player, List.of(offer)), Map.of(player, ClaimGroup.none()), completedAt,
                completedAt.plusSeconds(300), CompletedRunRecord.RecoveryStatus.LIVE,
                Map.of(player, "snapshot-ref"), List.of(new CompletedRunRecord.JournalBounds(
                "dungeon", 0, 0, 0, 10, 10, 10)));
        CompletedRunRecordCodec codec = new CompletedRunRecordCodec();

        byte[] first = codec.encode(original);
        byte[] second = codec.encode(original);
        CompletedRunRecord restored = codec.decode(first);

        assertArrayEquals(first, second);
        String json = new String(first, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schema-version\":1"));
        assertTrue(json.contains("\"serializedItem\":\"AQID\""));
        assertEquals(original, restored);
        assertArrayEquals(new byte[]{1, 2, 3}, restored.offers().get(player).getFirst().items().getFirst().serializedItem());
        assertThrows(UnsupportedOperationException.class,
                () -> restored.offers().get(player).add(offer));
    }

    @Test
    void rejectsMissingAndUnsupportedSchemaVersions() {
        CompletedRunRecordCodec codec = new CompletedRunRecordCodec();
        assertThrows(JsonParseException.class, () -> codec.decode("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(JsonParseException.class, () -> codec.decode(
                "{\"schema-version\":2,\"record\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsParticipantMapKeyThatDoesNotMatchEmbeddedPlayerId() {
        UUID mapKey = UUID.randomUUID();
        UUID embeddedPlayer = UUID.randomUUID();
        Instant completedAt = Instant.EPOCH;

        assertThrows(IllegalArgumentException.class, () -> new CompletedRunRecord(UUID.randomUUID(), "floor_1", 1,
                "config", "content", "v1",
                new ScoreResult(0, 0, 0, 0, 0, DungeonRank.D, List.of()),
                Map.of(mapKey, new CompletedRunRecord.ParticipantResult(embeddedPlayer, 0, true)), Set.of(),
                Map.of(), Map.of(), completedAt, completedAt.plusSeconds(1),
                CompletedRunRecord.RecoveryStatus.LIVE, Map.of(), List.of()));
    }
}
