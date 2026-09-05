package me.lidan.dungeonCrawlers.integration;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Refuses server/plugin reload dispatch while a completion transition is still pending. */
public final class BukkitReloadProtectionListener implements Listener {
    private static final String MESSAGE =
            "[FAIL] server reload is refused while dungeon reward completion is pending";
    private final BooleanSupplier completionPending;

    public BukkitReloadProtectionListener(BooleanSupplier completionPending) {
        this.completionPending = Objects.requireNonNull(completionPending, "completionPending");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (isReloadCommand(event.getCommand()) && blocked()) {
            event.setCancelled(true);
            event.getSender().sendMessage(MESSAGE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isReloadCommand(event.getMessage()) && blocked()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MESSAGE);
        }
    }

    static boolean isReloadCommand(String command) {
        if (command == null) return false;
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) normalized = normalized.substring(1).trim();
        if (normalized.isEmpty()) return false;
        String root = normalized.split("\\s+", 2)[0];
        return root.equals("reload") || root.equals("minecraft:reload") || root.equals("bukkit:reload");
    }

    private boolean blocked() {
        try {
            return completionPending.getAsBoolean();
        } catch (RuntimeException ignored) {
            // A failed safety predicate fails closed for reload dispatch.
            return true;
        }
    }
}
