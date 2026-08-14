package me.lidan.dungeonCrawlers.core.score;

public enum DungeonRank {
    D, C, B, A, S, S_PLUS;

    public static DungeonRank forScore(int score) {
        if (score < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        if (score >= 300) return S_PLUS;
        if (score >= 270) return S;
        if (score >= 230) return A;
        if (score >= 160) return B;
        if (score >= 100) return C;
        return D;
    }
}
