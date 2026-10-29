package me.lidan.dungeonCrawlers.events;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/** Successful Vault gateway debit or credit, never an affordability check. */
public final class DungeonCurrencyTransactionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final double amount;
    private final boolean created;
    public DungeonCurrencyTransactionEvent(UUID playerId, double amount, boolean created) {
        super(!Bukkit.isPrimaryThread());
        if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("positive finite amount required");
        this.playerId = Objects.requireNonNull(playerId); this.amount = amount; this.created = created;
    }
    public UUID getPlayerId() { return playerId; }
    public double getAmount() { return amount; }
    public boolean isCreated() { return created; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
