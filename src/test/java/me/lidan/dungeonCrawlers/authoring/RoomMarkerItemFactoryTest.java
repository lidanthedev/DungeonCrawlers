package me.lidan.dungeonCrawlers.authoring;

import me.lidan.dungeonCrawlers.core.template.RoomMarker;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomMarkerItemFactoryTest {
    @BeforeEach
    void setUpBukkit() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void everyKitItemHasAStableMarkerPdcIdAndUnrelatedItemsAreIgnored() {
        RoomMarkerItemFactory factory = new RoomMarkerItemFactory(null);

        assertEquals(RoomMarker.values().length, factory.createKit().size());
        for (RoomMarker marker : RoomMarker.values()) {
            ItemStack item = factory.create(marker);
            assertEquals(Optional.of(marker), factory.marker(item));
            assertTrue(factory.isSetupItem(item));
            assertTrue(item.getItemMeta().lore() != null && !item.getItemMeta().lore().isEmpty(), marker.id());
        }
        assertTrue(factory.marker(new ItemStack(Material.STONE)).isEmpty());
    }

    @Test
    void kitUsesTheCanonicalVanillaMaterials() {
        RoomMarkerItemFactory factory = new RoomMarkerItemFactory(null);

        assertEquals(Material.JIGSAW, factory.create(RoomMarker.ENTRANCE).getType());
        assertEquals(Material.JIGSAW, factory.create(RoomMarker.EXIT).getType());
        assertEquals(Material.EMERALD_BLOCK, factory.create(RoomMarker.PLAYER_SPAWN).getType());
        assertEquals(Material.GRAY_CONCRETE_POWDER, factory.create(RoomMarker.NORMAL_MOB).getType());
        assertEquals(Material.YELLOW_CONCRETE_POWDER, factory.create(RoomMarker.MINIBOSS_MOB).getType());
        assertEquals(Material.ORANGE_CONCRETE_POWDER, factory.create(RoomMarker.CLASS_SELECTOR_NPC).getType());
        assertEquals(Material.RED_CONCRETE_POWDER, factory.create(RoomMarker.BOSS_SPAWN).getType());
        assertEquals(Material.LIME_CONCRETE_POWDER, factory.create(RoomMarker.REWARD_CHEST).getType());
        assertEquals(Material.CHEST, factory.create(RoomMarker.BLESSING_CHEST).getType());
        assertEquals(Material.TRAPPED_CHEST, factory.create(RoomMarker.STANDARD_CHEST).getType());
        assertEquals(Material.FLINT_AND_STEEL, factory.create(RoomMarker.PORTAL).getType());
    }

    @Test
    void refreshRemovesOnlyPluginMarkerItems() {
        RoomMarkerItemFactory factory = new RoomMarkerItemFactory(null);
        PlayerMock player = new PlayerMock(MockBukkit.getMock(), "Builder");
        MockBukkit.getMock().addPlayer(player);
        player.getInventory().setItem(0, factory.create(RoomMarker.ENTRANCE));
        player.getInventory().setItem(1, new ItemStack(Material.JIGSAW));
        player.getInventory().setItem(2, new ItemStack(Material.STONE, 3));

        assertEquals(1, factory.removeSetupItems(player.getInventory()));
        assertTrue(player.getInventory().getItem(0) == null);
        assertEquals(Material.JIGSAW, player.getInventory().getItem(1).getType());
        assertEquals(3, player.getInventory().getItem(2).getAmount());
    }
}
