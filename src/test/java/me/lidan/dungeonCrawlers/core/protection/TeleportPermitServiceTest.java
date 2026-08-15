package me.lidan.dungeonCrawlers.core.protection;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportPermitServiceTest {
    @Test
    void permitRequiresMatchingDestinationAndExpires() {
        TeleportPermitService service = new TeleportPermitService();
        UUID player = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        service.authorize(player, Set.of(new TeleportPermitService.Destination("world", new Point(1, 64, 2))),
                now.plusSeconds(5));

        assertFalse(service.consume(player, "world", new Point(9, 64, 9), now.plusSeconds(1)));
        assertTrue(service.consume(player, "world", new Point(1, 64, 2), now.plusSeconds(1)));
        service.authorize(player, Set.of(new TeleportPermitService.Destination("world", new Point(1, 64, 2))),
                now.plusSeconds(5));
        assertFalse(service.consume(player, "world", new Point(1, 64, 2), now.plusSeconds(5)));
    }
}
