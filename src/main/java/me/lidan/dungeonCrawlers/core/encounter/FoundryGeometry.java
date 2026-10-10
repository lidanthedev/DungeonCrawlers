package me.lidan.dungeonCrawlers.core.encounter;

/** Coordinates are relative to the boss spawn's floor level. */
public final class FoundryGeometry {
    public static final int RADIUS = 32;
    public static final int DROP = 10;
    private FoundryGeometry() { }

    public static boolean lowerPlatform(double x, double z) {
        double radius = Math.hypot(x, z);
        return radius <= 8 || radius <= RADIUS && (Math.abs(x) <= 2 || Math.abs(z) <= 2)
                || radius >= 29 && radius <= RADIUS
                || Math.abs(Math.abs(x) - 17) <= 6 && Math.abs(Math.abs(z) - 17) <= 6
                || radius <= 29 && Math.abs(Math.abs(x) - Math.abs(z)) <= 1.5;
    }

    public static int quadrant(double x, double z) { return (x >= 0 ? 0 : 1) + (z >= 0 ? 0 : 2); }
    public static boolean lane(double x, double z, double angle, double width) {
        return Math.abs(z * Math.cos(angle) - x * Math.sin(angle)) <= width;
    }
    public static boolean wave(double x, double z, double radius, double width) {
        return Math.abs(Math.hypot(x, z) - radius) <= width;
    }
}
