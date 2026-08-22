package me.lidan.dungeonCrawlers.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitReloadProtectionListenerTest {
    @Test
    void recognizesServerReloadAliasesButNotDungeonReload() {
        assertTrue(BukkitReloadProtectionListener.isReloadCommand("reload"));
        assertTrue(BukkitReloadProtectionListener.isReloadCommand("/minecraft:reload confirm"));
        assertTrue(BukkitReloadProtectionListener.isReloadCommand("  bukkit:reload  "));
        assertFalse(BukkitReloadProtectionListener.isReloadCommand("dungeon reload force"));
        assertFalse(BukkitReloadProtectionListener.isReloadCommand("reloadable"));
    }
}
