package me.lidan.dungeonCrawlers.persistence.model;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationJournalCodecTest {
    private final GenerationJournalCodec codec = new GenerationJournalCodec();

    @Test
    void roundTripsRecoveryIdentityAndBounds() {
        GenerationJournal journal = journal();

        assertEquals(journal, codec.decode(codec.encode(journal)));
    }

    @Test
    void rejectsMissingAndUnsupportedVersions() {
        assertThrows(JsonParseException.class, () -> codec.decode("{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(JsonParseException.class, () -> codec.decode(
                "{\"schema-version\":2,\"journal\":{}}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsEveryMissingRequiredJournalField() {
        for (String field : new String[]{"instanceId", "seed", "slotId", "world", "plannedBounds",
                "participants", "configHash", "contentHash", "algorithmVersion", "status", "createdAt"}) {
            var envelope = JsonParser.parseString(new String(codec.encode(journal()), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            envelope.getAsJsonObject("journal").remove(field);

            assertThrows(JsonParseException.class,
                    () -> codec.decode(envelope.toString().getBytes(StandardCharsets.UTF_8)), field);
        }
    }

    private static GenerationJournal journal() {
        return new GenerationJournal(UUID.fromString("00000000-0000-0000-0000-000000000001"), 42, 3,
                "dungeon_instances", List.of(new GenerationJournal.PlannedBounds(1, 2, 3, 4, 5, 6)),
                List.of(UUID.fromString("00000000-0000-0000-0000-000000000002")), "config", "content",
                "phase2-v1", GenerationJournal.Status.PLANNED, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
