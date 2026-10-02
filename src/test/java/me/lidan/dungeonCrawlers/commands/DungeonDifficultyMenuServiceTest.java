package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.difficulty.Difficulty;
import me.lidan.dungeonCrawlers.core.difficulty.DungeonProgressionService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DungeonDifficultyMenuServiceTest {
    @Test void fillsUnusedSlotsWithGlassAndClosesWithoutStarting() {
        var server = MockBukkit.mock();
        try {
            dev.triumphteam.gui.TriumphGui.init(MockBukkit.createMockPlugin());
            var player = server.addPlayer();
            var configs = mock(ConfigRegistryService.class);
            when(configs.snapshot()).thenReturn(new ConfigSnapshot(1, Map.of(), Map.of(), Map.of(),
                    Map.of(), Set.of(), "test", Instant.EPOCH));
            var progression = mock(DungeonProgressionService.class);
            when(progression.isUnlocked(player.getUniqueId(), "floor_1", Difficulty.NORMAL)).thenReturn(true);
            new DungeonDifficultyMenuService(configs, progression, (ignored, args) -> fail("Opening must not start a run"))
                    .open(player, "floor_1");
            var inventory = player.getOpenInventory().getTopInventory();
            assertEquals(54, inventory.getSize());
            int[] slots = {19, 20, 21, 22, 23, 24, 25, 30, 31, 32};
            var controls = Set.of(13, 19, 20, 21, 22, 23, 24, 25, 30, 31, 32, 49);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                assertNotNull(inventory.getItem(slot));
                if (!controls.contains(slot)) {
                    assertEquals(Material.GRAY_STAINED_GLASS_PANE, inventory.getItem(slot).getType());
                }
            }
            var plain = PlainTextComponentSerializer.plainText();
            for (Difficulty tier : Difficulty.values()) {
                var item = inventory.getItem(slots[tier.ordinal()]);
                assertNotNull(item);
                assertEquals(tier.displayName() + (tier == Difficulty.NORMAL ? "" : " [Locked]"),
                        plain.serialize(item.getItemMeta().displayName()));
            }
            assertEquals(10, java.util.Arrays.stream(slots).mapToObj(inventory::getItem)
                    .map(item -> item.getType()).distinct().count());
            assertEquals(Material.BARRIER, inventory.getItem(49).getType());
            assertEquals("Close", plain.serialize(inventory.getItem(49).getItemMeta().displayName()));
            var close = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                    49, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            server.getPluginManager().callEvent(close);
            assertTrue(close.isCancelled());
            assertNotSame(inventory, player.getOpenInventory().getTopInventory());
        } finally { MockBukkit.unmock(); }
    }
}
