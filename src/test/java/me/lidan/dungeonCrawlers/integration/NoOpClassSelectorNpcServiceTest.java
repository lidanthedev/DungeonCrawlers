package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NoOpClassSelectorNpcServiceTest {
    @Test
    void unavailableIntegrationIsSafeToUseDuringEveryCleanupPath() {
        ClassSelectorNpcService service = new NoOpClassSelectorNpcService();

        assertFalse(service.available());
        assertDoesNotThrow(() -> {
            service.createFor(UUID.randomUUID(), new Location(null, 0, 0, 0));
            service.removeFor(UUID.randomUUID());
            service.shutdown();
        });
    }
}
