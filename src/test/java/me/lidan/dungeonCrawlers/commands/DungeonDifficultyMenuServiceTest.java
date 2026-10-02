package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.difficulty.Difficulty;
import me.lidan.dungeonCrawlers.core.difficulty.DungeonProgressionService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DungeonDifficultyMenuServiceTest {
    @Test void centersLastThreeTiersAndLeavesCornersEmpty() {
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
            assertEquals(27, inventory.getSize());
            for (int corner : new int[]{0, 8, 18, 26}) assertNull(inventory.getItem(corner));
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 21, 22, 23};
            var plain = PlainTextComponentSerializer.plainText();
            for (Difficulty tier : Difficulty.values()) {
                var item = inventory.getItem(slots[tier.ordinal()]);
                assertNotNull(item);
                assertEquals(tier.displayName() + (tier == Difficulty.NORMAL ? "" : " [Locked]"),
                        plain.serialize(item.getItemMeta().displayName()));
            }
            assertEquals(10, java.util.Arrays.stream(slots).mapToObj(inventory::getItem)
                    .map(item -> item.getType()).distinct().count());
        } finally { MockBukkit.unmock(); }
    }
}
