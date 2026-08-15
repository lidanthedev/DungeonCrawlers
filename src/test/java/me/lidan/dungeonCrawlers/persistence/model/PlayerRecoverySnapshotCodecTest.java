package me.lidan.dungeonCrawlers.persistence.model;

import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerRecoverySnapshotCodecTest {
    private final PlayerRecoverySnapshotCodec codec = new PlayerRecoverySnapshotCodec();

    @Test
    void roundTripsEverySavedAttribute() {
        PlayerRecoverySnapshot snapshot = snapshot();
        assertEquals(snapshot, codec.decode(codec.encode(snapshot)));
    }

    @Test
    void rejectsMissingRequiredAttribute() {
        var envelope = JsonParser.parseString(new String(codec.encode(snapshot()), StandardCharsets.UTF_8))
                .getAsJsonObject();
        envelope.getAsJsonObject("snapshot").remove("foodLevel");
        assertThrows(JsonParseException.class,
                () -> codec.decode(envelope.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static PlayerRecoverySnapshot snapshot() {
        return new PlayerRecoverySnapshot(UUID.randomUUID(), UUID.randomUUID(), "world", 1.25, 64, -3.75,
                90, -5, "SURVIVAL", 18.5, 17, 4.5f, 0.25f, 20, 280, 400,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
