package me.lidan.dungeonCrawlers.integration;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Enforces the one dungeon action-bar alert per player per second contract. */
public final class ThrottledDungeonActionBar implements DungeonActionBar {
    private final DungeonActionBar delegate;
    private final Clock clock;
    private final Duration cooldown;
    private final Map<UUID, Instant> nextAllowed = new HashMap<>();

    public ThrottledDungeonActionBar(DungeonActionBar delegate, Clock clock) {
        this(delegate, clock, Duration.ofSeconds(1));
    }

    public ThrottledDungeonActionBar(DungeonActionBar delegate, Clock clock, Duration cooldown) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
        if (cooldown.isNegative()) throw new IllegalArgumentException("cooldown must not be negative");
    }

    @Override
    public synchronized void show(Player player, Component message) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(message, "message");
        Instant now = clock.instant();
        Instant allowed = nextAllowed.get(player.getUniqueId());
        if (allowed != null && now.isBefore(allowed)) return;
        nextAllowed.put(player.getUniqueId(), now.plus(cooldown));
        delegate.show(player, message);
    }

    public synchronized void clear(UUID playerId) {
        nextAllowed.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public synchronized void clearAll() {
        nextAllowed.clear();
    }
}
