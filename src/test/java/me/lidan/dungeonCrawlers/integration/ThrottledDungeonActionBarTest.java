package me.lidan.dungeonCrawlers.integration;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThrottledDungeonActionBarTest {
    @Test
    void sendsAtMostOneAlertPerPlayerPerCooldown() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        MutableClock clock = new MutableClock();
        AtomicInteger shown = new AtomicInteger();
        ThrottledDungeonActionBar bar = new ThrottledDungeonActionBar((ignored, message) -> shown.incrementAndGet(),
                clock, Duration.ofSeconds(1));

        bar.show(player, Component.text("one"));
        bar.show(player, Component.text("two"));
        assertEquals(1, shown.get());

        clock.advance(Duration.ofSeconds(1));
        bar.show(player, Component.text("three"));
        assertEquals(2, shown.get());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;

        private void advance(Duration duration) { now = now.plus(duration); }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
