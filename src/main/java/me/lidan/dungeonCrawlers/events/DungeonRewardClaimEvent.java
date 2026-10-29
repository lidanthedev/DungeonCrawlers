package me.lidan.dungeonCrawlers.events;

import me.lidan.dungeonCrawlers.core.claim.RewardClaimService.ClaimRequest;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Cancellable request before a new reward purchase, never an owned reward delivery. */
public final class DungeonRewardClaimEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ClaimRequest request;
    private boolean cancelled;
    public DungeonRewardClaimEvent(ClaimRequest request) {
        super(!Bukkit.isPrimaryThread());
        this.request = Objects.requireNonNull(request);
    }
    public ClaimRequest getRequest() { return request; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
