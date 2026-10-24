package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A planted standard built from blocks; Minecraft's banner item transform cannot tip it sideways. */
final class BukkitSupportBanner implements AutoCloseable {
    private final Location center;
    private final Color color;
    private final float facingYaw;
    private final List<Part> parts = new ArrayList<>();
    private int ticks;
    private boolean closed;

    private BukkitSupportBanner(Location center, Color color) {
        this.facingYaw = center.getYaw();
        this.center = center.clone();
        // Display entity rotation also applies, so erase the caster's pitch at spawn.
        this.center.setYaw(0);
        this.center.setPitch(0);
        this.color = color;
    }

    static BukkitSupportBanner spawn(Location center, Material bannerMaterial, Color color) {
        Objects.requireNonNull(center.getWorld(), "banner world");
        BukkitSupportBanner banner = new BukkitSupportBanner(center, Objects.requireNonNull(color));
        try {
            banner.build(bannerMaterial);
            return banner;
        } catch (RuntimeException error) {
            banner.close();
            throw error;
        }
    }

    private void build(Material bannerMaterial) {
        Material cloth = switch (bannerMaterial) {
            case RED_BANNER -> Material.RED_WOOL;
            case CYAN_BANNER -> Material.CYAN_WOOL;
            default -> Material.WHITE_WOOL;
        };
        Material trim = bannerMaterial == Material.CYAN_BANNER ? Material.IRON_BLOCK : Material.GOLD_BLOCK;
        block(Material.POLISHED_DEEPSLATE, -.45, 0, -.45, .9, .18, .9, false);
        block(trim, -.30, .18, -.30, .6, .12, .6, false);
        block(Material.DARK_OAK_WOOD, -.06, .3, .10, .12, 3.38, .12, false);
        block(trim, -1.2, 3.3, -.12, 2.4, .14, .3, false);
        block(trim, -.14, 3.68, .02, .28, .20, .28, false);
        // Three shallow folds and a split hem give the cloth depth from both sides.
        block(cloth, -1.1, .95, -.06, .7, 2.35, .055, true);
        block(cloth, -.4, .95, -.10, .8, 2.35, .055, true);
        block(cloth, .4, .95, -.06, .7, 2.35, .055, true);
        block(trim, -1.1, .95, -.115, .055, 2.35, .16, true);
        block(trim, 1.045, .95, -.115, .055, 2.35, .16, true);
        block(trim, -1.1, .93, -.115, 2.2, .06, .16, true);
        block(cloth, -.90, .65, -.06, .75, .28, .055, true);
        block(cloth, .15, .65, -.06, .75, .28, .055, true);
        for (double z : new double[]{-.17, .07}) {
            if (bannerMaterial == Material.RED_BANNER) {
                block(trim, -.10, 1.50, z, .20, 1.35, .035, true);
                block(trim, -.45, 1.70, z, .90, .16, .035, true);
            } else if (bannerMaterial == Material.CYAN_BANNER) {
                block(trim, -.65, 2.25, z, 1.10, .18, .035, true);
                block(trim, -.35, 1.90, z, 1.00, .18, .035, true);
            } else {
                block(trim, -.11, 1.60, z, .22, 1.20, .035, true);
                block(trim, -.50, 2.10, z, 1.00, .22, .035, true);
            }
        }
    }

    private void block(Material material, double x, double y, double z,
                       double width, double height, double depth, boolean cloth) {
        // Track the entity before configuring it so failed initialization cannot leak a partial scene.
        BlockDisplay display = center.getWorld().spawn(center, BlockDisplay.class);
        Matrix4f transform = new Matrix4f().rotateY((float) -Math.toRadians(facingYaw))
                .translate((float) x, (float) y, (float) z)
                .scale((float) width, (float) height, (float) depth);
        parts.add(new Part(display, transform, cloth));
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBlock(material.createBlockData());
        display.setBillboard(Display.Billboard.FIXED);
        display.setViewRange(1.4F);
        display.setDisplayWidth(5);
        display.setDisplayHeight(5);
        display.setBrightness(new Display.Brightness(15, 0));
        display.setInterpolationDuration(20);
        display.setTransformationMatrix(transform);
    }

    void tick() {
        if (!isValid()) return;
        float sway = (float) Math.sin(++ticks * .65) * .025F;
        for (Part part : parts) {
            if (!part.cloth()) continue;
            part.display().setInterpolationDelay(0);
            part.display().setTransformationMatrix(new Matrix4f().rotateY((float) -Math.toRadians(facingYaw))
                    .translate(0, 0, sway).rotateY((float) Math.toRadians(facingYaw)).mul(part.transform()));
        }
        center.getWorld().spawnParticle(Particle.DUST, center.clone().add(0, 3.9, 0),
                3, .12, .10, .12, 0, new Particle.DustOptions(color, .8F));
    }

    boolean isValid() {
        return !closed && !parts.isEmpty() && parts.stream().allMatch(part -> part.display().isValid());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        parts.forEach(part -> part.display().remove());
        parts.clear();
    }

    private record Part(BlockDisplay display, Matrix4f transform, boolean cloth) { }
}
