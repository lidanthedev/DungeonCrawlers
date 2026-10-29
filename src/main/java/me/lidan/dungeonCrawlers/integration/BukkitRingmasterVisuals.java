package me.lidan.dungeonCrawlers.integration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Encounter-owned models use vanilla displays, so the spectacle needs no resource pack. */
public final class BukkitRingmasterVisuals {
    public static final int MAX_ENTITIES = 256;
    private final World world;
    private final String instanceTag;
    private final List<Rig> rigs = new ArrayList<>();
    private int entities;

    public BukkitRingmasterVisuals(World world, UUID instance) {
        this.world = world;
        instanceTag = "ringmaster:" + instance;
    }

    public Rig rig(Location at) {
        Rig rig = new Rig(at);
        rigs.add(rig);
        return rig;
    }

    public Rig jester(Location at, boolean encore) {
        Rig r = rig(at);
        r.block(Material.PURPLE_CONCRETE, 0, 1.9, -.55, 2.5, 2.3, .55);
        r.block(Material.WHITE_CONCRETE, 0, 3.15, 0, 1.35, 1.25, 1.1);
        r.block(Material.PURPLE_CONCRETE, 0, 3.87, 0, 1.65, .35, 1.25);
        for (int side : new int[]{-1, 1}) {
            r.block(Material.BLACK_CONCRETE, side * .32, 3.36, .57, .25, .32, .1);
            r.block(encore ? Material.REDSTONE_BLOCK : Material.YELLOW_CONCRETE,
                    side * .32, 3.39, .64, .1, .15, .04);
            Material hat = side == -1 ? Material.PURPLE_CONCRETE : Material.BLUE_CONCRETE;
            r.block(hat, side * .8, 4.2, 0, .55, .9, .7, side * -.35);
            r.block(hat, side * 1.15, 4.6, 0, .5, .7, .6, side * -1.05);
            r.block(hat, side * 1.6, 4.45, 0, .4, .55, .5, side * -1.8);
            r.block(Material.GOLD_BLOCK, side * 1.85, 4.1, 0, .42, .42, .42);
            r.block(Material.WHITE_CONCRETE, side * .95, 2.65, .1, .55, .4, .7, side * .35);
        }
        r.block(Material.REDSTONE_BLOCK, 0, 3.12, .7, .28, .28, .28);
        r.block(Material.BLACK_CONCRETE, 0, 2.88, .59, .8, .13, .08);
        for (double x : new double[]{-.27, 0, .27})
            r.block(Material.WHITE_CONCRETE, x, 2.9, .65, .17, .13, .05);
        if (encore) {
            r.block(Material.BLACK_CONCRETE, 0, 3.55, .62, .06, .85, .06, -.35);
            scytheParts(r, 1.7, 0, .25, .55);
            for (int side : new int[]{-1, 1})
                for (int i = 0; i < 3; i++)
                    r.block(Material.PURPLE_STAINED_GLASS, side * (1.45 + i * .4), 2.5 + i * .45, -.8,
                            .5, 1.6, .15, side * .8);
        } else {
            r.block(Material.GOLD_BLOCK, 1.65, 1.9, .1, .12, 2.4, .12);
            r.block(Material.PINK_CONCRETE, 1.65, 3.35, .1, .85, 1.05, .85);
            r.block(Material.WHITE_CONCRETE, 1.45, 3.6, .54, .18, .25, .05);
        }
        r.move(at, at.getYaw(), 0, 1);
        return r;
    }

    public Rig balloon(Location at, int color) {
        Rig r = rig(at);
        Material material = new Material[]{Material.PINK_CONCRETE, Material.CYAN_CONCRETE,
                Material.YELLOW_CONCRETE, Material.PURPLE_CONCRETE}[color % 4];
        r.block(material, 0, 0, 0, .85, 1.05, .85);
        r.block(Material.WHITE_CONCRETE, -.18, .22, .44, .17, .25, .04);
        r.block(material, 0, -.6, 0, .23, .18, .23);
        r.block(Material.WHITE_CONCRETE, 0, -.92, 0, .03, .55, .03);
        return r;
    }

    public Rig card(Location at, int suit) {
        Rig r = rig(at);
        r.block(Material.GOLD_BLOCK, 0, 0, 0, 1.1, 1.5, .14);
        r.block(Material.WHITE_CONCRETE, 0, 0, .09, .95, 1.34, .05);
        r.text(new String[]{"♣", "♦", "♠", "♥"}[suit % 4], 0, -.24, .13, 1.8,
                suit % 2 == 0 ? NamedTextColor.DARK_PURPLE : NamedTextColor.RED);
        return r;
    }

    public Rig box(Location at) {
        Rig r = rig(at);
        r.block(Material.PURPLE_CONCRETE, 0, 1, 0, 2, 2, 2);
        r.block(Material.GOLD_BLOCK, 0, 1, 0, .25, 2.1, 2.1);
        r.block(Material.GOLD_BLOCK, 0, 1, 0, 2.1, 2.1, .25);
        r.block(Material.PINK_CONCRETE, 0, 2.15, 0, 2.25, .25, 2.25);
        r.text("?", 0, .5, 1.1, 2.5, NamedTextColor.YELLOW);
        return r;
    }

    public Rig scythe(Location at) {
        Rig r = rig(at);
        scytheParts(r, 0, 0, 0, 1.5);
        return r;
    }

    private void scytheParts(Rig r, double x, double y, double z, double scale) {
        r.block(Material.BLACK_CONCRETE, x, y + 3 * scale, z, .18 * scale, 6 * scale, .2 * scale);
        r.block(Material.GOLD_BLOCK, x, y + 4.8 * scale, z, .3 * scale, .5 * scale, .3 * scale);
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI / 9;
            r.block(i < 5 ? Material.IRON_BLOCK : Material.PURPLE_CONCRETE,
                    x + Math.sin(a) * 2.3 * scale, y + (4.6 + Math.cos(a) * 1.3) * scale, z,
                    .65 * scale, .27 * scale, .15 * scale, -a);
        }
    }

    public Rig horse(Location at) {
        Rig r = rig(at);
        r.block(Material.GOLD_BLOCK, 0, 1.5, 0, .12, 3, .12);
        r.block(Material.WHITE_CONCRETE, 0, 1, 0, 1.5, .7, .6);
        r.block(Material.WHITE_CONCRETE, -.55, 1.65, 0, .55, .8, .6, -.25);
        r.block(Material.BLUE_CONCRETE, .2, 1.45, 0, .6, .3, .7);
        for (double x : new double[]{-.5, .5})
            r.block(Material.WHITE_CONCRETE, x, .35, 0, .18, .7, .4, x * .5);
        r.block(Material.GOLD_BLOCK, 0, 3.1, 0, .3, .3, .3);
        return r;
    }

    public void clear() {
        for (Rig rig : List.copyOf(rigs)) rig.close();
    }

    public int entityCount() { return entities; }

    public final class Rig {
        private final List<Part> parts = new ArrayList<>();
        private Location anchor;
        private boolean closed;
        private Rig(Location at) { anchor = at.clone(); }

        public void block(Material material, double x, double y, double z, double w, double h, double d) {
            block(material, x, y, z, w, h, d, 0);
        }

        public void block(Material material, double x, double y, double z, double w, double h, double d, double tilt) {
            checkBudget();
            BlockDisplay display = world.spawn(anchor, BlockDisplay.class, entity -> {
                configure(entity);
                entity.setBlock(material.createBlockData());
            });
            parts.add(new Part(display, new Matrix4f().translate((float) x, (float) y, (float) z)
                    .rotateZ((float) tilt).translate((float) -w / 2, (float) -h / 2, (float) -d / 2)
                    .scale((float) w, (float) h, (float) d)));
            entities++;
        }

        public void text(String label, double x, double y, double z, double scale, NamedTextColor color) {
            checkBudget();
            TextDisplay display = world.spawn(anchor, TextDisplay.class, entity -> {
                configure(entity);
                entity.text(Component.text(label, color));
                entity.setAlignment(TextDisplay.TextAlignment.CENTER);
                entity.setBackgroundColor(Color.fromARGB(0));
                entity.setShadowed(false);
                entity.setSeeThrough(false);
            });
            parts.add(new Part(display, new Matrix4f().translate((float) x, (float) y, (float) z).scale((float) scale)));
            entities++;
        }

        private void checkBudget() {
            if (closed || entities >= MAX_ENTITIES) throw new IllegalStateException("ringmaster display budget exceeded");
        }

        private void configure(Display display) {
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(1.8F);
            display.setTeleportDuration(2);
            display.setInterpolationDuration(2);
            display.setBillboard(Display.Billboard.FIXED);
            display.addScoreboardTag(instanceTag);
        }

        public void move(Location at, double yaw, double roll, double scale) {
            if (closed) return;
            anchor = at.clone();
            anchor.setYaw(0); anchor.setPitch(0);
            Matrix4f transform = new Matrix4f().rotateY((float) -Math.toRadians(yaw)).rotateZ((float) roll).scale((float) scale);
            for (Part part : parts) {
                part.display.teleport(anchor);
                part.display.setInterpolationDelay(0);
                part.display.setTransformationMatrix(new Matrix4f(transform).mul(part.transform));
            }
        }

        public void close() {
            if (closed) return;
            closed = true;
            for (Part part : parts) part.display.remove();
            entities -= parts.size();
            parts.clear();
            rigs.remove(this);
        }
    }

    private record Part(Display display, Matrix4f transform) { }
}
