package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread renderer for progress reported by asynchronous DungeonCrawlers work. */
public final class BukkitProgressBarService implements ProgressBarService {
    private final Plugin plugin;
    private final Map<UUID, ActiveTask> active = new HashMap<>();
    private volatile boolean closed;

    public BukkitProgressBarService(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void begin(UUID taskId, Collection<? extends Player> players, String title, String detail,
                      double progress) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(detail, "detail");
        runOnMain(() -> {
            remove(taskId);
            ActiveTask task = new ActiveTask(title, players);
            active.put(taskId, task);
            task.render(progress, detail, BarColor.BLUE);
        });
    }

    @Override
    public void update(UUID taskId, double progress, String detail) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(detail, "detail");
        runOnMain(() -> {
            ActiveTask task = active.get(taskId);
            if (task != null) task.render(progress, detail, BarColor.BLUE);
        });
    }

    @Override
    public void complete(UUID taskId, String detail) {
        finish(taskId, detail, BarColor.GREEN, true);
    }

    @Override
    public void fail(UUID taskId, String detail) {
        finish(taskId, detail, BarColor.RED, false);
    }

    @Override
    public void cancel(UUID taskId) {
        Objects.requireNonNull(taskId, "taskId");
        runOnMain(() -> remove(taskId));
    }

    @Override
    public void cancelAll() {
        closed = true;
        if (Bukkit.isPrimaryThread()) {
            removeAll();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, this::removeAll);
        }
    }

    private void finish(UUID taskId, String detail, BarColor color, boolean successful) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(detail, "detail");
        runOnMain(() -> {
            ActiveTask task = active.get(taskId);
            if (task == null) return;
            task.render(successful ? 1.0 : task.progress, detail, color);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> remove(taskId), successful ? 20L : 60L);
        });
    }

    private void remove(UUID taskId) {
        ActiveTask task = active.remove(taskId);
        if (task != null) task.remove();
    }

    private void removeAll() {
        active.values().forEach(ActiveTask::remove);
        active.clear();
    }

    private void runOnMain(Runnable callback) {
        if (closed) return;
        Runnable guarded = () -> {
            if (!closed) callback.run();
        };
        if (Bukkit.isPrimaryThread()) guarded.run();
        else plugin.getServer().getScheduler().runTask(plugin, guarded);
    }

    private static double clamp(double progress) {
        if (!Double.isFinite(progress)) return 0.0;
        return Math.max(0.0, Math.min(1.0, progress));
    }

    private static String barTitle(String title, String detail) {
        return title + "  |  " + detail;
    }

    private static final class ActiveTask {
        private final Map<UUID, BossBar> bars = new HashMap<>();
        private double progress;

        private ActiveTask(String title, Collection<? extends Player> players) {
            for (Player player : players) {
                if (player == null || !player.isOnline()) continue;
                BossBar bar = Bukkit.createBossBar(title, BarColor.BLUE, BarStyle.SOLID);
                bar.addPlayer(player);
                bars.put(player.getUniqueId(), bar);
            }
        }

        private void render(double progress, String detail, BarColor color) {
            this.progress = clamp(progress);
            bars.values().forEach(bar -> {
                bar.setProgress(this.progress);
                bar.setColor(color);
                bar.setTitle(barTitle(bar.getTitle() == null ? "DungeonCrawlers" : baseTitle(bar.getTitle()), detail));
                bar.setVisible(true);
            });
        }

        private void remove() {
            bars.values().forEach(bar -> {
                bar.removeAll();
                bar.setVisible(false);
            });
            bars.clear();
        }

        private static String baseTitle(String title) {
            int separator = title.indexOf("  |  ");
            return separator < 0 ? title : title.substring(0, separator);
        }
    }
}
