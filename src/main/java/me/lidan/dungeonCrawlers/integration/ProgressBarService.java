package me.lidan.dungeonCrawlers.integration;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/** Provider-neutral lifecycle for progress shown while a long task runs. */
public interface ProgressBarService {
    void begin(UUID taskId, Collection<? extends Player> players, String title, String detail, double progress);

    void update(UUID taskId, double progress, String detail);

    void complete(UUID taskId, String detail);

    void fail(UUID taskId, String detail);

    void cancel(UUID taskId);

    /** Removes every active bar during plugin shutdown or provider reset. */
    default void cancelAll() { }
}
