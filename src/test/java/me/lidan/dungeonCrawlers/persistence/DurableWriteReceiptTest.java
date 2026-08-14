package me.lidan.dungeonCrawlers.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DurableWriteReceiptTest {
    @Test
    void rejectsInvalidPersistedIdentityValues() {
        UUID operation = UUID.randomUUID();
        Path path = Path.of("record.bin");
        Instant committedAt = Instant.EPOCH;
        assertThrows(IllegalArgumentException.class,
                () -> new DurableWriteReceipt(operation, "key", -1, "checksum", path, committedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new DurableWriteReceipt(operation, "", 0, "checksum", path, committedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new DurableWriteReceipt(operation, "key", 0, "", path, committedAt));
    }
}
