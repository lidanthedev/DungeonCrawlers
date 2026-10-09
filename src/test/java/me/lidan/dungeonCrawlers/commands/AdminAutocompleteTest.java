package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAutocompleteTest {
    private ServerMock server;
    private PlayerMock admin;
    private CombatRoomService combat;
    private RunPreparationService runs;
    private DungeonRoomPreviewCommand preview;
    private final List<String> items = new ArrayList<>();
    private final List<String> mobs = new ArrayList<>();
    private final List<UUID> enemies = new ArrayList<>();
    private final List<UUID> claims = new ArrayList<>();

    @BeforeEach void registerProductionCommands() {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        admin = server.addPlayer("Admin");
        admin.setOp(true);
        combat = mock(CombatRoomService.class);
        runs = mock(RunPreparationService.class);
        var builder = BukkitLamp.builder(plugin);
        AdminSuggestionProviders.register(builder, combat, runs, () -> items, () -> mobs, () -> enemies, () -> claims);
        builder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
            if (annotation.value() == FloorIdSuggestionProvider.class) return new FloorIdSuggestionProvider<BukkitCommandActor>(() -> List.of("floor_1", "floor_2"));
            if (annotation.value() == RoomIdSuggestionProvider.class) return new RoomIdSuggestionProvider<BukkitCommandActor>(() -> List.of("crypt"));
            if (annotation.value() == ClassIdSuggestionProvider.class) return new ClassIdSuggestionProvider<BukkitCommandActor>(() -> List.of("mage"));
            if (annotation.value() == BlessingIdSuggestionProvider.class) return new BlessingIdSuggestionProvider<BukkitCommandActor>(() -> List.of("life"));
            if (annotation.value() == OfflinePlayerSuggestionProvider.class) return new OfflinePlayerSuggestionProvider<BukkitCommandActor>(() -> List.of("Admin"));
            if (annotation.value() == InstanceIdSuggestionProvider.class) return new InstanceIdSuggestionProvider<BukkitCommandActor>(List::of);
            if (annotation.value() == RewardIdSuggestionProvider.class) return new RewardIdSuggestionProvider<BukkitCommandActor>(() -> List.of("gold"));
            return null;
        });
        var lamp = builder.build();
        for (Class<?> type : List.of(DungeonAuthoringCommand.class, DungeonCrawlersCommand.class,
                DungeonGenerationCommand.class, DungeonRoomPreviewCommand.class, DungeonDifficultyDebugCommand.class, DungeonPhaseFourCommand.class,
                DungeonPhaseSixCommand.class, DungeonPhaseSevenCommand.class, DungeonPhaseEightCommand.class,
                DungeonPhaseNineCommand.class, DungeonPhaseElevenCommand.class, DungeonProgressionCommand.class)) {
            var command = mock(type);
            if (type == DungeonRoomPreviewCommand.class) preview = (DungeonRoomPreviewCommand) command;
            lamp.register(command);
        }
    }

    @AfterEach void closeServer() { MockBukkit.unmock(); }

    @Test void completesFixedChoicesAndConfiguredIdsAcrossAdminCommands() {
        for (String command : List.of("room create new_room", "selection validate")) {
            assertChoices("dungeon " + command + " ", Set.of("normal", "start", "portal", "boss"));
            assertChoices("dungeon " + command + " n", Set.of("normal"));
            assertChoices("dungeon " + command + " normal ",
                    Set.of("none", "normal", "miniboss", "normal,miniboss", "miniboss,normal"));
            assertChoices("dungeon " + command + " normal normal,", Set.of("normal,miniboss"));
            assertChoices("dungeon " + command + " normal miniboss,n", Set.of("miniboss,normal"));
            assertChoices("dungeon " + command + " normal none,", Set.of());
        }
        for (var entry : Map.ofEntries(
                Map.entry("room paste crypt ", Set.of("0", "90", "180", "270")),
                Map.entry("connect-test crypt crypt ", Set.of("0", "90", "180", "270")),
                Map.entry("door register-test this 1 2 3 ", Set.of("north", "east", "south", "west")),
                Map.entry("door set this ", Set.of("locked", "ready", "open")),
                Map.entry("state simulate ", Set.of("generating", "preparing", "running", "boss", "completion_pending", "completed", "failed", "destroyed")),
                Map.entry("state simulate running ", Set.of("generating", "preparing", "running", "boss", "completion_pending", "completed", "failed", "destroyed")),
                Map.entry("reward reconcile " + UUID.randomUUID() + " ", Set.of("charged", "not-charged")),
                Map.entry("reward delivery-pause-test ", Set.of("on", "off", "true", "false")),
                Map.entry("score simulate ", Set.of("true", "false")),
                Map.entry("class info ", Set.of("mage")),
                Map.entry("blessing info ", Set.of("life")),
                Map.entry("instance generate-difficulty-debug ", Set.of("floor_1", "floor_2")),
                Map.entry("instance generate-difficulty-debug floor_1 h", Set.of("hard", "hardcore", "hellish")),
                Map.entry("completions add Admin ", Set.of("floor_1", "floor_2")),
                Map.entry("room update ", Set.of("crypt")),
                Map.entry("room preview c", Set.of("crypt")),
                Map.entry("player revive this ", Set.of("Admin")),
                Map.entry("boss start ", Set.of("this")),
                Map.entry("blessing add this ", Set.of("life")),
                Map.entry("reward info ", Set.of("this"))).entrySet()) {
            assertChoices("dungeon " + entry.getKey(), entry.getValue());
        }
        assertChoices("dungeon room create ", Set.of());
        server.dispatchCommand(admin, "dungeon room preview stop");
        verify(preview).stop(admin);
        verify(preview, never()).preview(any(), eq("stop"));
    }

    @Test void scopesRoomsAndLiveEntitiesToTheSelectedInstanceAndRoom() {
        UUID instance = UUID.randomUUID(), other = UUID.randomUUID();
        UUID alive = UUID.randomUUID(), dead = UUID.randomUUID(), secondRoom = UUID.randomUUID();
        when(runs.instanceFor(admin.getUniqueId())).thenReturn(java.util.Optional.of(instance));
        when(combat.rooms(instance)).thenReturn(List.of(room(1, alive, dead), room(2, secondRoom, null)));
        when(combat.rooms(other)).thenReturn(List.of(room(8, UUID.randomUUID(), null)));
        for (String command : List.of("room activate", "room clear", "mob list", "mob spawn", "mob kill", "mob remove")) {
            assertChoices("dungeon " + command + " " + instance + " ", Set.of("1", "2"));
            assertChoices("dungeon " + command + " this ", Set.of("1", "2"));
            assertChoices("dungeon " + command + " " + other + " ", Set.of("8"));
            assertChoices("dungeon " + command + " invalid ", Set.of());
        }
        for (String command : List.of("mob kill", "mob remove")) {
            assertChoices("dungeon " + command + " this 1 ", Set.of(alive.toString()));
            assertChoices("dungeon " + command + " " + instance + " 2 ", Set.of(secondRoom.toString()));
            assertChoices("dungeon " + command + " " + instance + " 99 ", Set.of());
        }
        assertEquals(List.of(), server.getCommandMap().tabComplete(server.getConsoleSender(), "dungeon room activate this "));
        when(combat.rooms(instance)).thenReturn(List.of());
        assertChoices("dungeon room activate this ", Set.of());
        verify(combat, never()).activate(any(), anyInt());
        verify(combat, never()).kill(any(), anyInt(), any());
        verify(combat, never()).remove(any(), anyInt(), any());
    }

    @Test void refreshesNativeAndRuntimeIdsWithoutExposingAdminChoicesToPlayers() {
        UUID enemy = UUID.randomUUID(), claim = UUID.randomUUID();
        items.add("RUNIC_FRAGMENT"); mobs.add("CryptZombie"); enemies.add(enemy); claims.add(claim);
        assertChoices("dungeon compatibility item RUNIC_", Set.of("RUNIC_FRAGMENT"));
        assertChoices("dungeon compatibility mythic Crypt", Set.of("CryptZombie"));
        assertChoices("dungeon mob spawn this 1 Crypt", Set.of("CryptZombie"));
        assertChoices("dungeon runic force ", Set.of(enemy.toString()));
        assertChoices("dungeon reward reconcile ", Set.of(claim.toString()));
        items.clear(); mobs.clear(); enemies.clear(); claims.clear();
        for (String command : List.of("compatibility item", "compatibility mythic", "mob spawn this 1", "runic force", "reward reconcile")) {
            assertChoices("dungeon " + command + " ", Set.of());
        }
        var player = server.addPlayer("Regular");
        for (String command : List.of("room create new_room", "room preview", "compatibility item", "mob spawn this 1", "runic force")) {
            assertEquals(List.of(), server.getCommandMap().tabComplete(player, "dungeon " + command + " "));
        }
    }

    private void assertChoices(String command, Set<String> expected) {
        assertEquals(expected, Set.copyOf(server.getCommandMap().tabComplete(admin, command)), command);
    }

    private static CombatRoomService.RoomSnapshot room(int index, UUID alive, UUID dead) {
        var mobs = new ArrayList<CombatRoomService.MobSnapshot>();
        mobs.add(new CombatRoomService.MobSnapshot("CryptZombie", new Point(0, 0, 0), alive,
                CombatRoomService.MobState.ALIVE, 1, false, "alive"));
        if (dead != null) mobs.add(new CombatRoomService.MobSnapshot("CryptZombie", new Point(0, 0, 0), dead,
                CombatRoomService.MobState.DEAD, 1, false, "dead"));
        mobs.add(new CombatRoomService.MobSnapshot("CryptZombie", new Point(0, 0, 0), null,
                CombatRoomService.MobState.MISSING, 1, false, "missing"));
        return new CombatRoomService.RoomSnapshot(index, "crypt", CombatRoomService.RoomState.ACTIVE, "test", mobs);
    }
}
