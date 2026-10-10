package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.encounter.FoundryGeometry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An encounter owns every display and original block state; no explosions or falling blocks. */
public final class BukkitFoundryScene implements AutoCloseable {
    public static final int DISPLAY_CAP = 220;
    public static final int BLOCK_CAP = 6000;
    private final Location center;
    private final String tag;
    private final List<BlockDisplay> displays = new ArrayList<>();
    private final Map<Block, BlockState> originals = new LinkedHashMap<>();
    private final List<Piece> pieces = new ArrayList<>();
    private final List<List<BlockDisplay>> chains = new ArrayList<>();
    private final List<BlockDisplay> crown = new ArrayList<>();
    private final List<BlockDisplay> hammers = new ArrayList<>();
    private BlockDisplay movingBridge;
    private int bridgeAxis;
    private int detached;
    private boolean closed;

    public BukkitFoundryScene(Location center, UUID instance) {
        this.center = center.clone();
        tag = "foundry:" + instance;
    }

    public BlockDisplay block(Material material, double x, double y, double z, double sx, double sy, double sz) {
        if (closed || displays.size() >= DISPLAY_CAP) throw new IllegalStateException("foundry display budget exhausted");
        BlockDisplay display = center.getWorld().spawn(center, BlockDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.addScoreboardTag(tag);
            entity.setBlock(material.createBlockData());
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(2);
            entity.setViewRange(1.4F);
            entity.setBrightness(new Display.Brightness(15, 0));
        });
        displays.add(display);
        move(display, x, y, z, sx, sy, sz, 0);
        return display;
    }

    public void move(BlockDisplay display, double x, double y, double z, double sx, double sy, double sz, double roll) {
        display.setInterpolationDelay(0);
        display.setTransformationMatrix(new Matrix4f().translate((float) x, (float) y, (float) z)
                .rotateZ((float) roll).scale((float) sx, (float) sy, (float) sz).translate(-.5F, -.5F, -.5F));
    }

    public void change(int x, int y, int z, Material material) {
        if (closed) return;
        Block block = center.clone().add(x, y, z).getBlock();
        if (Math.abs(x) > 38 || Math.abs(z) > 38 || y < -12 || y > 33)
            throw new IllegalArgumentException("foundry mutation outside arena");
        if (!originals.containsKey(block)) {
            if (originals.size() >= BLOCK_CAP) throw new IllegalStateException("foundry block budget exhausted");
            originals.put(block, block.getState());
        }
        block.setType(material, false);
    }

    public void beginTransformation() {
        Material chain = Material.matchMaterial("IRON_CHAIN");
        if (chain == null) chain = Material.matchMaterial("CHAIN");
        if (chain == null) throw new IllegalStateException("server has no chain material");
        for (int side = 0; side < 8; side++) {
            List<BlockDisplay> links = new ArrayList<>();
            for (int i = 0; i < 14; i++) links.add(block(chain, 0, 31, 0, .01, .01, .01));
            chains.add(links);
        }
        for (int side = 0; side < 4; side++) {
            double angle = Math.PI / 4 + side * Math.PI / 2;
            int x = (int) Math.round(18 * Math.sqrt(2) * Math.cos(angle));
            int z = (int) Math.round(18 * Math.sqrt(2) * Math.sin(angle));
            BlockDisplay pier = block(Material.POLISHED_DEEPSLATE, x, 8, z, .01, .01, .01);
            BlockDisplay capital = block(Material.OXIDIZED_CUT_COPPER, x, 17, z, .01, .01, .01);
            pieces.add(new Piece(pier, x, 8, z, 3, 16, 3, side));
            pieces.add(new Piece(capital, x, 17, z, 5, 2, 5, side));
        }
        for (int side = 0; side < 8; side++) {
            double angle = side * Math.PI / 4;
            double x = 18 * Math.cos(angle), z = 18 * Math.sin(angle);
            BlockDisplay deck = block(Material.DEEPSLATE_TILES, x, -.5, z, .01, .01, .01);
            pieces.add(new Piece(deck, x, -.5, z, 12, 1, 12, side));
        }
    }

    public void transform(double progress) {
        double extension = Math.clamp(progress / .32, 0, 1);
        double pull = Math.clamp((progress - .35) / .5, 0, 1);
        for (int side = 0; side < chains.size(); side++) {
            double angle = side < 4 ? Math.PI / 4 + side * Math.PI / 2 : (side - 4) * Math.PI / 2;
            double endRadius = (side < 4 ? 18 * Math.sqrt(2) : 18) + pull * 12;
            double endY = 2 + pull * 16;
            List<BlockDisplay> links = chains.get(side);
            for (int i = 0; i < links.size(); i++) {
                double t = (i + .5) / links.size() * extension;
                double radius = 36 + (endRadius - 36) * t;
                double y = 29 + (endY - 29) * t - Math.sin(t * Math.PI) * (1 - pull) * 4;
                double size = extension <= i / (double) links.size() ? .01 : 1.4;
                link(links.get(i), radius * Math.cos(angle), y, radius * Math.sin(angle), angle,
                        Math.atan2(36 - endRadius, endY - 29), size);
            }
        }
        int desired = Math.min(8, (int) Math.floor(Math.clamp((progress - .32) / .35, 0, 1) * 8));
        while (detached < desired) detach(detached++);
        for (Piece piece : pieces) {
            boolean exposed = piece.side < detached;
            double travel = exposed ? pull : 0;
            double radius = Math.hypot(piece.x, piece.z);
            double scale = exposed ? 1 : .001;
            move(piece.display, piece.x + piece.x / radius * travel * 12, piece.y + travel * 19,
                    piece.z + piece.z / radius * travel * 12, piece.sx * scale, piece.sy * scale, piece.sz * scale,
                    travel * (piece.side % 2 == 0 ? .45 : -.45));
        }
    }

    private void detach(int side) {
        for (int x = -32; x <= 32; x++) for (int z = -32; z <= 32; z++) {
            if (Math.hypot(x, z) > 32) continue;
            int sector = (int) Math.floor((Math.atan2(z, x) + Math.PI * 2 + Math.PI / 8) % (Math.PI * 2) / (Math.PI / 4));
            if (sector == side) change(x, -1, z, Material.AIR);
        }
        if (side < 4) {
            int x = side == 0 || side == 3 ? 18 : -18;
            int z = side < 2 ? 18 : -18;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                for (int y = 0; y <= 15; y++) change(x + dx, y, z + dz, Material.AIR);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                for (int y = 16; y <= 17; y++) change(x + dx, y, z + dz, Material.AIR);
            change(x, 18, z, Material.AIR);
        }
    }

    public void reveal() {
        transform(1);
        // Breaking the cold heart's shell exposes the incandescent furnace beneath.
        for (int x = -4; x <= 4; x++) for (int y = 11; y <= 19; y++) for (int z = -4; z <= 4; z++) {
            if (Math.abs(x) + Math.abs(y - 15) + Math.abs(z) <= 5)
                change(x, y, z, (x + y + z) % 3 == 0 ? Material.SHROOMLIGHT : Material.MAGMA_BLOCK);
        }
    }

    public void drawBridge(int axis, boolean retract) {
        if (closed) return;
        bridgeAxis = axis;
        if (retract && movingBridge == null) movingBridge = block(Material.POLISHED_BLACKSTONE_BRICKS, 0, 32, 0, .01, .01, .01);
        for (int r = 10; r <= 27; r++) for (int width = -2; width <= 2; width++) {
            int x = axis % 2 == 0 ? r * (axis == 0 ? 1 : -1) : width;
            int z = axis % 2 == 0 ? width : r * (axis == 1 ? 1 : -1);
            if (FoundryGeometry.lowerPlatform(x, z)) change(x, -11, z,
                    retract ? Material.AIR : Material.POLISHED_BLACKSTONE_BRICKS);
        }
        if (!retract && movingBridge != null) move(movingBridge, 0, 32, 0, .01, .01, .01, 0);
    }

    public void animateBridge(double progress) {
        if (movingBridge == null || closed) return;
        double angle = bridgeAxis * Math.PI / 2;
        double radius = 18.5 + progress * 16;
        double y = -10.5 + Math.sin(progress * Math.PI / 2) * 5;
        boolean alongX = bridgeAxis % 2 == 0;
        move(movingBridge, Math.cos(angle) * radius, y, Math.sin(angle) * radius, alongX ? 18 : 5, 1,
                alongX ? 5 : 18, progress * .15);
        if (chains.size() > bridgeAxis + 4) for (int i = 0; i < chains.get(bridgeAxis + 4).size(); i++) {
            double t = (i + .5) / 14D;
            double r = 36 + (radius - 36) * t;
            link(chains.get(bridgeAxis + 4).get(i), r * Math.cos(angle), 29 + (y - 29) * t,
                    r * Math.sin(angle), angle, Math.atan2(36 - radius, y - 29), 1.4);
        }
    }
    private void link(BlockDisplay display, double x, double y, double z, double angle, double lean, double size) {
        display.setInterpolationDelay(0);
        display.setTransformationMatrix(new Matrix4f().translate((float) x, (float) y, (float) z)
                .rotateY((float) -angle).rotateZ((float) lean).scale((float) size, (float) (size * 1.7), (float) size)
                .translate(-.5F, -.5F, -.5F));
    }

    public void bossCrown(Location boss, double intensity, long time) {
        if (crown.isEmpty()) {
            for (int i = 0; i < 8; i++) crown.add(block(Material.OXIDIZED_CUT_COPPER, 0, 0, 0, .5, 1.8, .5));
            crown.add(block(Material.AMETHYST_BLOCK, 0, 0, 0, 1.1, 1.1, 1.1));
        }
        double bx = boss.getX() - center.getX(), by = boss.getY() - center.getY(), bz = boss.getZ() - center.getZ();
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4 + time / (intensity > 1 ? 700D : 1800D);
            move(crown.get(i), bx + Math.cos(angle) * 2, by + 2.7 + Math.sin(angle * 2) * .3,
                    bz + Math.sin(angle) * 2, .45, 1.5 * intensity, .45, Math.sin(angle) * .3);
        }
        move(crown.get(8), bx, by + 3.1, bz, .9, .9, .9, time / 1500D);
    }

    public void guillotine(int safeQuadrant, double progress, boolean lowered) {
        if (hammers.isEmpty()) for (int i = 0; i < 4; i++)
            hammers.add(block(Material.ANVIL, 0, 28, 0, .01, .01, .01));
        for (int q = 0; q < 4; q++) {
            int x = q % 2 == 0 ? 17 : -17, z = q < 2 ? 17 : -17;
            double height = (lowered ? -10 : 0) + 18 * (1 - progress * progress) + 1;
            double size = q == safeQuadrant ? .01 : 5;
            move(hammers.get(q), x, height, z, size, size, size, Math.sin(progress * Math.PI) * .2);
        }
    }
    public void hideHammers() {
        for (BlockDisplay hammer : hammers) move(hammer, 0, 32, 0, .001, .001, .001, 0);
    }

    public void death(double progress) {
        for (int side = 0; side < chains.size(); side++) {
            double a = side * Math.PI / 4;
            for (int i = 0; i < chains.get(side).size(); i++) {
                double r = 20 + i;
                move(chains.get(side).get(i), Math.cos(a) * r, 25 - progress * 33 + i * .3,
                        Math.sin(a) * r, 1.4, 2.1, 1.4, progress * 3 * (side % 2 == 0 ? 1 : -1));
            }
        }
        for (Piece piece : pieces) {
            double back = 1 - progress;
            double r = Math.hypot(piece.x, piece.z);
            move(piece.display, piece.x + piece.x / r * 12 * back, piece.y + 19 * back,
                    piece.z + piece.z / r * 12 * back, piece.sx, piece.sy, piece.sz, back * .45);
        }
    }

    public void restore() {
        originals.values().forEach(state -> state.update(true, false));
        originals.clear();
    }
    public int displayCount() { return displays.size(); }
    public int changedBlockCount() { return originals.size(); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        try { restore(); }
        finally { displays.forEach(BlockDisplay::remove); displays.clear(); pieces.clear(); chains.clear(); crown.clear(); hammers.clear(); }
    }
    private record Piece(BlockDisplay display, double x, double y, double z, double sx, double sy, double sz, int side) { }
}
