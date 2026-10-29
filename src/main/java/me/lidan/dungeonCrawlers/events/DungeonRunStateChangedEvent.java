package me.lidan.dungeonCrawlers.events;

import me.lidan.dungeonCrawlers.core.run.RunPreparationService.StateChange;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Immutable observation published after the corresponding successful state commit. */
public final class DungeonRunStateChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final StateChange change;
    public DungeonRunStateChangedEvent(StateChange change) {
        super(!Bukkit.isPrimaryThread());
        this.change = Objects.requireNonNull(change);
    }
    public StateChange getChange() { return change; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
