package me.lidan.dungeonCrawlers.core.encounter;

import java.util.List;

/** Continuous pad occupancy; a departed partner immediately changes the solo requirement. */
public final class CounterweightTrial {
    public record Position(double x, double z) { }
    private final long holdMillis;
    private long occupiedSince = -1, lastSample = -1;
    private boolean complete;

    public CounterweightTrial(long holdMillis) {
        if (holdMillis <= 0) throw new IllegalArgumentException("counterweight hold must be positive");
        this.holdMillis = holdMillis;
    }

    public boolean update(long now, List<Position> fighters) {
        if (complete) return true;
        boolean west = fighters.stream().anyMatch(p -> onPad(p, -5));
        boolean east = fighters.stream().anyMatch(p -> onPad(p, 5));
        boolean occupied = !fighters.isEmpty() && (fighters.size() == 1 ? west || east : west && east);
        if (!occupied || lastSample >= 0 && (now < lastSample || now - lastSample > 500)) occupiedSince = -1;
        if (occupied && occupiedSince < 0) occupiedSince = now;
        lastSample = now;
        complete = occupiedSince >= 0 && now - occupiedSince >= holdMillis;
        return complete;
    }

    public double progress(long now) {
        return complete ? 1 : occupiedSince < 0 ? 0 : Math.clamp((now - occupiedSince) / (double) holdMillis, 0, 1);
    }

    private static boolean onPad(Position p, double x) {
        return Math.pow(p.x() - x, 2) + p.z() * p.z() <= 2.25;
    }
}
