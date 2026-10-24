package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.Placement;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitFoundryRoomsTest {
    @Test void bellLabelsReplaceSignsFollowRotationAndAreRemovedWithTheClue() throws Exception {
        for (Rotation rotation : Rotation.values()) checkHolograms("foundry_resonance", rotation, 5);
    }
    @Test void counterweightKeepsItsClueWithoutSpawningBellLabels() throws Exception {
        checkHolograms("foundry_counterweight", Rotation.NONE, 1);
    }
    @SuppressWarnings("unchecked")
    private void checkHolograms(String template, Rotation rotation, int count) throws Exception {
        UUID instance = UUID.randomUUID();
        var adapter = new BukkitFoundryRooms(mock(Plugin.class), mock(GenerationService.class),
                mock(RunPreparationService.class), mock(PlayerLifecycleService.class), mock(CombatRoomService.class),
                mock(TeleportPermitService.class), Clock.systemUTC(), "dungeons");
        Placement placement = mock(Placement.class);
        when(placement.templateId()).thenReturn(template);
        when(placement.rotation()).thenReturn(rotation);
        when(placement.origin()).thenReturn(new Point(100, 64, 200));
        Class<?> roomType = Class.forName(BukkitFoundryRooms.class.getName() + "$Room");
        var constructor = roomType.getDeclaredConstructor(Placement.class, long.class); constructor.setAccessible(true);
        Object room = constructor.newInstance(placement, 3003L);
        World world = mock(World.class); Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        var displays = new ArrayList<TextDisplay>(); var positions = new ArrayList<Location>();
        var texts = new ArrayList<String>();
        when(world.spawn(any(Location.class), eq(TextDisplay.class), any(Consumer.class))).thenAnswer(call -> {
            TextDisplay display = mock(TextDisplay.class);
            doAnswer(text -> {
                texts.add(PlainTextComponentSerializer.plainText().serialize(text.getArgument(0))); return null;
            }).when(display).text(any());
            displays.add(display); positions.add(call.getArgument(0));
            ((Consumer<TextDisplay>) call.getArgument(2)).accept(display); return display;
        });
        var clue = BukkitFoundryRooms.class.getDeclaredMethod("clue", UUID.class, roomType, Player.class);
        clue.setAccessible(true);
        Object display = clue.invoke(adapter, instance, room, player);
        var clueField = roomType.getDeclaredField("clue"); clueField.setAccessible(true); clueField.set(room, display);
        assertEquals(count, displays.size());
        if (count == 5) {
            String[] names = {"Ember", "Tide", "Storm", "Void"};
            int[][] pads = {{-8, -8}, {8, -8}, {8, 8}, {-8, 8}};
            for (int i = 0; i < 4; i++) {
                assertTrue(texts.get(i).startsWith(names[i] + "\nRight-click"));
                Point expected = rotation.apply(new Point(24 + pads[i][0], 4, 26 + pads[i][1]))
                        .add(placement.origin());
                assertEquals(new Location(world, expected.x() + .5, expected.y(), expected.z() + .5), positions.get(i));
            }
            assertTrue(texts.getLast().contains("speaks first. Its opposite answers."));
        } else assertTrue(texts.getFirst().contains("Hold opposite plates for three seconds."));
        for (TextDisplay hologram : displays) {
            verify(hologram).setPersistent(false); verify(hologram).addScoreboardTag("foundry:" + instance);
        }
        var roomsField = BukkitFoundryRooms.class.getDeclaredField("rooms"); roomsField.setAccessible(true);
        ((Map<UUID, Map<Integer, Object>>) roomsField.get(adapter)).put(instance, new HashMap<>(Map.of(0, room)));
        adapter.cleanup(instance); adapter.cleanup(instance);
        for (TextDisplay hologram : displays) verify(hologram, times(1)).remove();
    }
}
