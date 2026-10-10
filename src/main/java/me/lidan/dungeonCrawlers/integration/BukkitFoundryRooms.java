package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.encounter.FoundryPuzzle;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.Placement;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.BlockDisplay;
import org.joml.Matrix4f;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Uses generated placements, real room-clear gates, blessing chests, and the shared cleanup path. */
public final class BukkitFoundryRooms implements Listener {
    private static final String[] RUNES = {"Ember", "Tide", "Storm", "Void"};
    private static final int[][] PADS = {{-8, -8}, {8, -8}, {8, 8}, {-8, 8}};
    private final Plugin plugin;
    private final GenerationService generation;
    private final RunPreparationService runs;
    private final PlayerLifecycleService lifecycle;
    private final CombatRoomService combat;
    private final TeleportPermitService permits;
    private final Clock clock;
    private final String world;
    private final Map<UUID, Map<Integer, Room>> rooms = new HashMap<>();
    private long lastTick;

    public BukkitFoundryRooms(Plugin plugin, GenerationService generation, RunPreparationService runs,
                             PlayerLifecycleService lifecycle, CombatRoomService combat,
                             TeleportPermitService permits, Clock clock, String world) {
        this.plugin = plugin; this.generation = generation; this.runs = runs; this.lifecycle = lifecycle;
        this.combat = combat; this.permits = permits; this.clock = clock;
        this.world = world;
    }

    public boolean complete(UUID instance, int index) {
        Room room = room(instance, index);
        return room == null || room.puzzle == null || room.puzzle.solved();
    }
    private Room room(UUID instance, int index) {
        var context = generation.layoutContext(instance).orElse(null);
        if (context == null || !context.floor().id().equals("floor_3")) return null;
        Placement placement = context.plan().placements().stream().filter(p -> p.index() == index && p.templateId().startsWith("foundry_"))
                .findFirst().orElse(null);
        if (placement == null) return null;
        return rooms.computeIfAbsent(instance, ignored -> new HashMap<>()).computeIfAbsent(index, ignored ->
                new Room(placement, context.seed()));
    }
    private List<Player> players(UUID instance, Placement placement) {
        return runs.info(instance).map(run -> run.participants().stream().map(plugin.getServer()::getPlayer)
                .filter(p -> p != null && p.isOnline() && alive(instance, p))
                .filter(p -> p.getWorld().getName().equals(world()) && placement.bounds().contains(point(p.getLocation()))).toList()).orElse(List.of());
    }
    private String world() { return world; }
    private boolean alive(UUID instance, Player player) {
        return runs.instanceFor(player.getUniqueId()).filter(instance::equals).isPresent()
                && lifecycle.player(instance, player.getUniqueId()).filter(p -> p.online()
                && p.state() == PlayerLifecycleService.PlayerState.ALIVE).isPresent();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        UUID instance = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (instance == null || !alive(instance, player)) return;
        var context = generation.layoutContext(instance).orElse(null);
        if (context == null || !context.floor().id().equals("floor_3")) return;
        Point clicked = point(event.getClickedBlock().getLocation());
        Placement placement = context.plan().placements().stream().filter(p -> p.bounds().contains(clicked)).findFirst().orElse(null);
        if (placement == null || !player.getWorld().getName().equals(world())) return;
        Room room = room(instance, placement.index());
        if (room == null || room.puzzle == null || !active(instance, placement.index())) return;
        if (placement.templateId().equals("foundry_resonance")) {
            int rune = room.rune(clicked);
            if (rune < 0) return;
            event.setCancelled(true);
            var result = room.puzzle.press(rune);
            float pitch = (float) Math.pow(2, rune / 6D);
            player.getWorld().playSound(event.getClickedBlock().getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .8F, pitch);
            if (result == FoundryPuzzle.Result.RESET) {
                players(instance, placement).forEach(p -> DungeonMessages.send(p, "<red>The chord fractures. Begin the verse again."));
            } else if (result == FoundryPuzzle.Result.SOLVED) solved(instance, room);
            else DungeonMessages.send(player, "<aqua>Resonance " + room.puzzle.progress() + "/4");
        } else if (clicked.equals(room.local(24, 3, 23))) {
            event.setCancelled(true);
            room.latch = !room.latch;
            DungeonMessages.send(player, "<gold>Maintenance latch " + (room.latch ? "engaged" : "released") + ". Solo operators still need one plate.");
        }
    }
    private boolean active(UUID instance, int index) {
        return combat.info(instance).map(state -> state.rooms().stream().anyMatch(room -> room.index() == index
                && room.state() == CombatRoomService.RoomState.ACTIVE)).orElse(false);
    }
    public void tick(Instant now) {
        long time = now.toEpochMilli();
        if (time - lastTick < 200) return;
        lastTick = time;
        for (var instance : combat.instances()) for (var snapshot : instance.rooms()) {
            if (snapshot.state() != CombatRoomService.RoomState.ACTIVE) continue;
            Room room = room(instance.instanceId(), snapshot.index());
            if (room == null) continue;
            List<Player> players = players(instance.instanceId(), room.placement);
            if (players.isEmpty()) continue;
            if (!room.announced) {
                room.announced = true;
                String name = room.placement.templateId().replace("foundry_", "").replace('_', ' ');
                players.forEach(p -> DungeonMessages.send(p, "<aqua>The Foundry · " + name));
            }
            if (room.puzzle != null && room.clue == null) room.clue = clue(instance.instanceId(), room, players.getFirst());
            if (room.placement.templateId().equals("foundry_counterweight") && !room.puzzle.solved()) {
                Location left = at(room.local(16, 3, 28), players.getFirst());
                Location right = at(room.local(32, 3, 28), players.getFirst());
                UUID a = holder(players, left), b = holder(players, right);
                int alive = runs.info(instance.instanceId()).orElseThrow().participants().stream().map(plugin.getServer()::getPlayer)
                        .filter(p -> p != null && alive(instance.instanceId(), p)).toList().size();
                var result = room.puzzle.balance(a, b, alive, room.latch, time);
                if (result == FoundryPuzzle.Result.SOLVED) solved(instance.instanceId(), room);
                else if (result == FoundryPuzzle.Result.ADVANCED) players.forEach(p -> p.spawnParticle(Particle.END_ROD,
                        at(room.local(24, 6, 28), p), 2, .2, .2, .2, 0));
            }
            if (time - room.lastAmbient >= 2500) {
                room.lastAmbient = time;
                for (Player player : players) {
                    player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_STEP, .2F, .6F);
                    player.spawnParticle(Particle.WAX_OFF, player.getLocation().add(0, 3, 0), 3, 3, 1, 3, 0);
                }
            }
            if (room.placement.templateId().equals("foundry_catwalk")) traversal(instance.instanceId(), room, players);
            if (room.placement.templateId().equals("foundry_turbine")) {
                Location center = at(room.local(28, 3, 28), players.getFirst());
                double angle = time / 1600D;
                if (room.rotors.isEmpty()) for (int i = 0; i < 2; i++)
                    room.rotors.add(center.getWorld().spawn(center.clone().add(0, 6, 0), BlockDisplay.class, display -> {
                        display.setBlock(Material.CUT_COPPER.createBlockData()); display.setPersistent(false);
                        display.addScoreboardTag("foundry:" + instance.instanceId()); display.setInterpolationDuration(4);
                    }));
                for (int i = 0; i < room.rotors.size(); i++) {
                    room.rotors.get(i).setInterpolationDelay(0);
                    room.rotors.get(i).setTransformationMatrix(new Matrix4f().rotateY((float) (angle + i * Math.PI / 2))
                            .translate(-10, 0, -.5F).scale(20, .4F, 1));
                }
                var dust = new Particle.DustOptions(Color.fromRGB(255, 200, 70), 1.6F);
                for (int radius = 9; radius <= 24; radius += 2) for (Player player : players)
                    player.spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * radius, .2, Math.sin(angle) * radius),
                            1, 0, 0, 0, 0, dust);
            }
        }
    }
    private void traversal(UUID instance, Room room, List<Player> players) {
        for (Player player : players) {
            Point at = point(player.getLocation());
            if (player.getY() >= room.placement.origin().y() + 3 && player.isOnGround()) room.checkpoints.put(player.getUniqueId(), at);
            if (player.getY() < room.placement.origin().y() + 2) {
                Point target = room.checkpoints.getOrDefault(player.getUniqueId(), room.local(20, 3, 8));
                Location destination = at(target, player);
                permits.authorize(player.getUniqueId(), Set.of(new TeleportPermitService.Destination(player.getWorld().getName(), target)),
                        clock.instant().plusSeconds(2));
                if (!player.teleport(destination)) permits.revoke(player.getUniqueId());
                player.setFallDistance(0);
            }
        }
    }
    private TextDisplay clue(UUID instance, Room room, Player player) {
        boolean resonance = room.placement.templateId().equals("foundry_resonance");
        if (resonance) for (int i = 0; i < RUNES.length; i++)
            room.runeLabels.add(hologram(instance, player, room.local(24 + PADS[i][0], 4, 26 + PADS[i][1]),
                    "<aqua><bold>" + RUNES[i] + "</bold>\n<gray>Right-click the rune or bell"));
        String text = resonance ? "<aqua><bold>The remembered verse</bold>\n<gold>"
                + RUNES[room.puzzle.sequence().getFirst()] + " speaks first. Its opposite answers.\n"
                + "Then the next clockwise from the first.\nThe final bell closes the verse."
                : "<aqua>Two souls hold the engine in balance.\n<gold>Hold opposite plates for three seconds.\n<gray>Alone: engage the maintenance latch.";
        return hologram(instance, player, room.local(24, 7, resonance ? 24 : 28), text);
    }
    private TextDisplay hologram(UUID instance, Player player, Point point, String text) {
        return player.getWorld().spawn(at(point, player), TextDisplay.class, display -> {
            display.setPersistent(false); display.addScoreboardTag("foundry:" + instance);
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.text(MiniMessageUtils.miniMessage(text));
            display.setViewRange(.8F);
        });
    }
    private void solved(UUID instance, Room room) {
        if (room.clue != null) room.clue.text(MiniMessageUtils.miniMessage("<green><bold>The mechanism is restored."));
        players(instance, room.placement).forEach(p -> {
            DungeonMessages.send(p, "<green>The seal yields. Defeat any remaining enemies to open the next room.");
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .7F, 1.2F);
        });
        combat.refreshObjective(instance, room.placement.index());
    }
    private static UUID holder(List<Player> players, Location pad) {
        return players.stream().filter(p -> p.getLocation().distanceSquared(pad) < 1.4 * 1.4 && p.isOnGround())
                .map(Player::getUniqueId).findFirst().orElse(null);
    }
    private static Location at(Point point, Player player) { return new Location(player.getWorld(), point.x() + .5, point.y(), point.z() + .5); }
    private static Point point(Location location) { return new Point(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    public void cleanup(UUID instance) {
        Map<Integer, Room> state = rooms.remove(instance);
        if (state != null) state.values().forEach(room -> {
            if (room.clue != null) room.clue.remove(); room.rotors.forEach(BlockDisplay::remove);
            room.runeLabels.forEach(TextDisplay::remove);
        });
    }
    private static final class Room {
        private final Placement placement;
        private final FoundryPuzzle puzzle;
        private final Map<UUID, Point> checkpoints = new HashMap<>();
        private final List<BlockDisplay> rotors = new ArrayList<>();
        private final List<TextDisplay> runeLabels = new ArrayList<>();
        private TextDisplay clue;
        private boolean latch, announced;
        private long lastAmbient;
        private Room(Placement placement, long seed) {
            this.placement = placement;
            puzzle = switch (placement.templateId()) {
                case "foundry_resonance" -> new FoundryPuzzle(FoundryPuzzle.Kind.RESONANCE, seed + placement.index());
                case "foundry_counterweight" -> new FoundryPuzzle(FoundryPuzzle.Kind.COUNTERWEIGHT, seed);
                default -> null;
            };
        }
        private Point local(int x, int y, int z) { return placement.rotation().apply(new Point(x, y, z)).add(placement.origin()); }
        private int rune(Point point) {
            for (int i = 0; i < PADS.length; i++) if (point.equals(local(24 + PADS[i][0], 3, 24 + PADS[i][1]))
                    || point.equals(local(24 + PADS[i][0], 5, 24 + PADS[i][1]))) return i;
            return -1;
        }
    }
}
