package me.lidan.dungeonCrawlers.integration;

import org.bukkit.command.CommandSender;

import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime switch for diagnostics and implementation-only command output. */
public final class DebugSettings {
    private final AtomicBoolean enabled;

    public DebugSettings(boolean enabled) {
        this.enabled = new AtomicBoolean(enabled);
    }

    public boolean enabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    public boolean require(CommandSender sender) {
        if (enabled()) return true;
        DungeonMessages.send(sender, DungeonMessages.warning(
                "This is a debug-only command and is disabled in production."));
        return false;
    }
}
