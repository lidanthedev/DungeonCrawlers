package me.lidan.dungeonCrawlers.core.encounter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Deterministic, instance-owned mechanisms; the adapters render clues and accept real interactions. */
public final class FoundryPuzzle {
    public enum Kind { RESONANCE, COUNTERWEIGHT }
    public enum Result { ADVANCED, RESET, WAITING, SOLVED, ALREADY_SOLVED }
    private final Kind kind;
    private final List<Integer> sequence;
    private int progress;
    private boolean solved;
    private long heldSince = -1;

    public FoundryPuzzle(Kind kind, long seed) {
        this.kind = kind;
        int first = Math.floorMod(seed, 4);
        sequence = List.of(first, (first + 2) % 4, (first + 1) % 4, (first + 3) % 4);
    }
    public List<Integer> sequence() { return sequence; }
    public int progress() { return progress; }
    public boolean solved() { return solved; }
    public Result press(int rune) {
        if (solved) return Result.ALREADY_SOLVED;
        if (kind != Kind.RESONANCE || rune < 0 || rune > 3) return Result.WAITING;
        if (rune != sequence.get(progress)) { progress = 0; return Result.RESET; }
        if (++progress == sequence.size()) { solved = true; return Result.SOLVED; }
        return Result.ADVANCED;
    }
    /** Each alive participant can hold only one plate; solo uses the reachable maintenance latch. */
    public Result balance(UUID left, UUID right, int alivePlayers, boolean latch, long millis) {
        if (solved) return Result.ALREADY_SOLVED;
        if (kind != Kind.COUNTERWEIGHT) return Result.WAITING;
        Set<UUID> holders = new HashSet<>();
        if (left != null) holders.add(left);
        if (right != null) holders.add(right);
        boolean balanced = alivePlayers == 1 ? latch && !holders.isEmpty() : holders.size() == 2;
        if (!balanced) { heldSince = -1; return Result.WAITING; }
        if (heldSince < 0) heldSince = millis;
        if (millis - heldSince >= 3000) { solved = true; return Result.SOLVED; }
        return Result.ADVANCED;
    }
}
