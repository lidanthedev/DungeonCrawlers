package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Limits;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.difficulty.DungeonProgressionService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.snapshot.PlayerSnapshotService;
import me.lidan.dungeonCrawlers.integration.DungeonActionBar;
import me.lidan.dungeonCrawlers.integration.PartyProvider;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DungeonStartPermissionTest {
    private final ConfigRegistryService configs = mock(ConfigRegistryService.class);
    private final PartyProvider parties = mock(PartyProvider.class);
    private final GenerationService generation = mock(GenerationService.class);
    private PlayerMock player;
    private org.bukkit.plugin.Plugin plugin;
    private DungeonPhaseFiveCommand command;

    @BeforeEach void setup() {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        dev.triumphteam.gui.TriumphGui.init(plugin);
        player = server.addPlayer();
        command = new DungeonPhaseFiveCommand(configs, parties, generation, mock(RunPreparationService.class),
                mock(PlayerSnapshotService.class), new TeleportPermitService(), server, plugin,
                Clock.systemUTC(), "dungeon_instances", mock(DungeonActionBar.class));
        when(parties.lookup(player.getUniqueId())).thenReturn(new PartyProvider.PartyLookup(
                PartyProvider.Status.SOLO, player.getUniqueId(), List.of(player.getUniqueId()), "solo"));
        when(generation.start(any())).thenReturn(GenerationService.StartResult.failure("test admission closed"));
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private void floor(String id) {
        var floor = mock(FloorDefinition.class);
        when(floor.id()).thenReturn(id);
        when(floor.displayName()).thenReturn("Test floor");
        when(floor.limits()).thenReturn(new Limits(5, 512, 1000, 256, 2, 1000));
        when(configs.snapshot()).thenReturn(new ConfigSnapshot(1, Map.of(id, floor), Map.of(), Map.of(),
                Map.of(), Set.of(), "test", Instant.EPOCH));
    }

    @ParameterizedTest @ValueSource(strings = {"floor_1", "floor_3", "custom_floor"})
    void deniesMissingFloorPermissionBeforePartyLookupOrGeneration(String id) {
        floor(id);
        player.addAttachment(plugin, "dungeoncrawlers.use", true);
        player.addAttachment(plugin, "dungeoncrawlers.start.other_floor", true);
        command.start(player, id);
        command.start(player, id, "normal");
        command.startDebug(player, id, "normal", 1);
        assertTrue(player.nextMessage().contains("You do not have permission to start Test floor"));
        verifyNoInteractions(parties, generation);
    }

    @ParameterizedTest @ValueSource(strings = {"floor_1", "floor_3", "custom_floor"})
    void allowsTheExactFloorGrant(String id) {
        floor(id);
        player.addAttachment(plugin, "dungeoncrawlers.use", true);
        player.addAttachment(plugin, "dungeoncrawlers.start." + id, true);
        command.start(player, id, "normal");
        verify(generation).start(argThat(request -> request.floor().id().equals(id)));
    }

    @Test void menuClickRechecksRevokedFloorPermission() {
        floor("floor_1");
        player.addAttachment(plugin, "dungeoncrawlers.use", true);
        var grant = player.addAttachment(plugin, "dungeoncrawlers.start.floor_1", true);
        command.configureDifficulties(mock(DungeonProgressionService.class), ignored -> { });
        command.start(player, "floor_1");
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
        grant.setPermission("dungeoncrawlers.start.floor_1", false);
        var click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                19, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        // Triumph's static listener belongs to the first MockBukkit server in the test JVM.
        var gui = (dev.triumphteam.gui.guis.BaseGui) player.getOpenInventory().getTopInventory().getHolder();
        gui.getGuiItem(19).getAction().execute(click);
        assertTrue(click.isCancelled());
        assertTrue(player.nextMessage().contains("You do not have permission to start Test floor"));
        verifyNoInteractions(generation);
        verify(parties, times(1)).lookup(player.getUniqueId());
    }

    @Test void menuCallbackAlsoRechecksBaseUsePermission() {
        floor("floor_1");
        player.addAttachment(plugin, "dungeoncrawlers.use", false);
        player.addAttachment(plugin, "dungeoncrawlers.start.floor_1", true);
        command.start(player, "floor_1", "normal");
        verifyNoInteractions(parties, generation);
    }

    @Test void pluginDeclaresBundledFloorPermissionsForOperators() throws Exception {
        try (var stream = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            var description = new org.bukkit.plugin.PluginDescriptionFile(stream);
            for (String id : List.of("floor_1", "floor_3")) {
                var permission = description.getPermissions().stream()
                        .filter(entry -> entry.getName().equals("dungeoncrawlers.start." + id)).findFirst().orElseThrow();
                assertEquals(org.bukkit.permissions.PermissionDefault.OP, permission.getDefault());
            }
        }
    }
}
