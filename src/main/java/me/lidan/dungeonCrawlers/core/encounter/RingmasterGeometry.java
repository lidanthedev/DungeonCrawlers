package me.lidan.dungeonCrawlers.core.encounter;

public final class RingmasterGeometry {
    public static final double RADIUS = 42;
    public static final double HUB_RADIUS = 3.75;
    private RingmasterGeometry() { }

    public static boolean onStage(double x, double z) {
        double radius = Math.hypot(x, z);
        return radius >= HUB_RADIUS && radius <= RADIUS;
    }

    public static int sector(double x, double z) {
        double angle = (Math.atan2(z, x) + Math.PI / 4 + Math.PI * 2) % (Math.PI * 2);
        return (int) (angle / (Math.PI / 2));
    }

    public static boolean inSweep(double x, double z, double angle) {
        return onStage(x, z) && x * Math.cos(angle) + z * Math.sin(angle) >= 0
                && Math.abs(z * Math.cos(angle) - x * Math.sin(angle)) <= 1;
    }

    /** Two seconds of warning, then one full rotation over eight seconds. */
    public static double carouselAngle(double initial, long elapsedMillis) {
        return initial + Math.PI * 2 * Math.clamp((elapsedMillis - 2000) / 8000D, 0, 1);
    }

    public static boolean inDeckGap(double angle, double gap) {
        return Math.abs(Math.atan2(Math.sin(angle - gap), Math.cos(angle - gap))) < Math.PI / 7;
    }
}
