package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Virtual beacons need no sky access and never alter the arena's blocks. */
public final class BukkitRewardChestVisuals implements AutoCloseable {
    private final Map<UUID, Visual> visuals = new HashMap<>();
    private final Predicate<UUID> instanceOpen;

    public BukkitRewardChestVisuals(Predicate<UUID> instanceOpen) {
        this.instanceOpen = java.util.Objects.requireNonNull(instanceOpen);
    }

    public void show(UUID instance, Location chest) {
        remove(instance);
        Location center = chest.clone().add(.5, 1, .5);
        center.setYaw(0);
        center.setPitch(0);
        var visual = new Visual(center, new ArrayList<>());
        try {
            double height = Math.max(1, center.getWorld().getMaxHeight() - center.getY() - .55);
            block(visual, Material.BEACON, -.25, .05, -.25, .5, .5, .5);
            block(visual, Material.YELLOW_STAINED_GLASS, -.16, .55, -.16, .32, height, .32);
            block(visual, Material.WHITE_STAINED_GLASS, -.06, .55, -.06, .12, height, .12);
            visuals.put(instance, visual);
        } catch (RuntimeException error) {
            visual.displays().forEach(BlockDisplay::remove);
            throw error;
        }
    }

    private static void block(Visual visual, Material material, double x, double y, double z,
                              double width, double height, double depth) {
        BlockDisplay display = visual.center().getWorld().spawn(visual.center(), BlockDisplay.class);
        visual.displays().add(display);
        display.setBlock(material.createBlockData());
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(3);
        display.setDisplayWidth(1);
        display.setDisplayHeight((float) height);
        display.setTransformationMatrix(new Matrix4f().translate((float) x, (float) y, (float) z)
                .scale((float) width, (float) height, (float) depth));
    }

    /** Runs in the plugin's existing once-per-second task. */
    public void tick() {
        visuals.entrySet().removeIf(entry -> {
            Visual visual = entry.getValue();
            if (!instanceOpen.test(entry.getKey()) || visual.displays().stream().anyMatch(display -> !display.isValid())) {
                visual.displays().forEach(BlockDisplay::remove);
                return true;
            }
            Location center = visual.center();
            center.getWorld().spawnParticle(Particle.END_ROD, center, 12, .7, .5, .7, .02);
            for (int index = 0; index < 16; index++) {
                double angle = index * Math.PI / 8;
                center.getWorld().spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * .9, -.25,
                        Math.sin(angle) * .9), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.YELLOW, 1));
            }
            return false;
        });
    }

    private void remove(UUID instance) {
        Visual previous = visuals.remove(instance);
        if (previous != null) previous.displays().forEach(BlockDisplay::remove);
    }

    @Override public void close() {
        visuals.values().forEach(visual -> visual.displays().forEach(BlockDisplay::remove));
        visuals.clear();
    }

    private record Visual(Location center, List<BlockDisplay> displays) { }
}
