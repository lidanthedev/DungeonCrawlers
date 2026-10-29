package me.lidan.dungeonCrawlers.events;

import me.lidan.dungeonCrawlers.core.claim.RewardClaimService.DeliveredReward;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Immutable observation published after the corresponding successful state commit. */
public final class DungeonRewardDeliveredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DeliveredReward delivery;
    public DungeonRewardDeliveredEvent(DeliveredReward delivery) {
        super(!Bukkit.isPrimaryThread());
        this.delivery = Objects.requireNonNull(delivery);
    }
    public DeliveredReward getDelivery() { return delivery; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
