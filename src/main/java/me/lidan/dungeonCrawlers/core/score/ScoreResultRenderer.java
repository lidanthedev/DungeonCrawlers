package me.lidan.dungeonCrawlers.core.score;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.config.DungeonTimings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;

import java.time.Duration;
import java.util.Objects;

/** Builds the multiline player-facing score result with category hovers. */
public final class ScoreResultRenderer {
    private static final Duration DEFAULT_FREE_TIME = DungeonTimings.defaults().scoreFreeTime();
    private static final Duration DEFAULT_PENALTY_INTERVAL = DungeonTimings.defaults().scorePenaltyInterval();

    private ScoreResultRenderer() { }

    public static Component render(ScoreService.ScoreReport report) {
        Objects.requireNonNull(report, "report");
        return render(report, DEFAULT_FREE_TIME, DEFAULT_PENALTY_INTERVAL);
    }

    public static Component render(ScoreService.ScoreReport report, Duration freeTime,
                                   Duration penaltyInterval) {
        Objects.requireNonNull(report, "report");
        return render(report.result(), report.snapshot(), requirePositive("free time", freeTime),
                requirePositive("penalty interval", penaltyInterval));
    }

    public static Component render(ScoreService.ScoreReport report, Duration freeTime) {
        return render(report, freeTime, DEFAULT_PENALTY_INTERVAL);
    }

    public static Component render(ScoreService.ScoreResult result) {
        Objects.requireNonNull(result, "result");
        return render(result, null, DEFAULT_FREE_TIME, DEFAULT_PENALTY_INTERVAL);
    }

    private static Component render(ScoreService.ScoreResult result, ScoreService.FinalScoreSnapshot snapshot,
                                    Duration freeTime, Duration penaltyInterval) {
        boolean successful = snapshot == null || snapshot.successful();
        Component rendered = MiniMessageUtils.miniMessage(successful
                ? "<green><bold>Dungeon Complete!</bold></green>"
                : "<red><bold>Dungeon Failed!</bold></red>");
        rendered = line(rendered, "<gray>Dungeon Score: <white>" + result.total() + "</white></gray>");
        rendered = line(rendered, "<gray>Rank: " + rankColor(result.rank())
                + displayRank(result.rank()) + "</" + rankColorName(result.rank()) + "></gray>");
        rendered = rendered.append(Component.newline()).append(category("Skill", result.skill(),
                skillDetails(result, snapshot)));
        rendered = rendered.append(Component.newline()).append(category("Time", result.time(),
                timeDetails(result, snapshot, freeTime, penaltyInterval)));
        rendered = rendered.append(Component.newline()).append(category("Exploration", result.exploration(),
                explorationDetails(result, snapshot)));
        rendered = rendered.append(Component.newline()).append(category("Bonus", result.bonus(),
                bonusDetails(result)));
        return rendered;
    }

    private static Component category(String name, int value, Component details) {
        return MiniMessageUtils.miniMessage("<gray>" + name + ": <white>" + value + "/100</white></gray>")
                .hoverEvent(HoverEvent.showText(details));
    }

    private static Component skillDetails(ScoreService.ScoreResult result,
                                          ScoreService.FinalScoreSnapshot snapshot) {
        Component details = MiniMessageUtils.miniMessage("<yellow><bold>Skill Score</bold></yellow>");
        if (snapshot != null) {
            details = line(details, "<gray>Deaths: <white>" + snapshot.deaths() + "</white></gray>");
            if (!snapshot.successful()) {
                details = line(details, "<red>The run failed, so skill is forced to 0.</red>");
            } else {
                details = line(details, "<gray>Base: <white>100</white></gray>");
                details = line(details, "<gray>Death penalty: <white>-"
                        + Math.max(0, 100 - result.skill()) + "</white></gray>");
            }
        }
        return line(details, "<gray>Final: <white>" + result.skill() + "</white></gray>");
    }

    private static Component timeDetails(ScoreService.ScoreResult result,
                                         ScoreService.FinalScoreSnapshot snapshot, Duration freeTime,
                                         Duration penaltyInterval) {
        Component details = MiniMessageUtils.miniMessage("<yellow><bold>Time Score</bold></yellow>");
        if (snapshot != null) {
            Duration elapsed = snapshot.elapsed();
            long overtimeMillis = Math.max(0, elapsed.minus(freeTime).toMillis());
            long penaltyIntervals = (overtimeMillis + penaltyInterval.toMillis() - 1)
                    / penaltyInterval.toMillis();
            details = line(details, "<gray>Time taken: <white>" + formatDuration(elapsed) + "</white></gray>");
            details = line(details, "<gray>Free time: <white>" + formatDuration(freeTime) + "</white></gray>");
            details = line(details, "<gray>Penalty intervals: <white>" + penaltyIntervals
                    + "</white> (" + formatDuration(penaltyInterval) + ")</gray>");
        }
        return line(details, "<gray>Final: <white>" + result.time() + "</white></gray>");
    }

    private static Component explorationDetails(ScoreService.ScoreResult result,
                                                ScoreService.FinalScoreSnapshot snapshot) {
        Component details = MiniMessageUtils.miniMessage("<yellow><bold>Exploration Score</bold></yellow>");
        if (snapshot != null) {
            details = line(details, "<gray>Secrets: <white>" + snapshot.foundSecrets() + "/"
                    + snapshot.totalSecrets() + "</white></gray>");
        }
        return line(details, "<gray>Final: <white>" + result.exploration() + "</white></gray>");
    }

    private static Component bonusDetails(ScoreService.ScoreResult result) {
        Component details = MiniMessageUtils.miniMessage("<yellow><bold>Bonus Score</bold></yellow>");
        if (result.bonusFacts().isEmpty()) details = line(details, "<gray>No bonus applied.</gray>");
        for (ScoreService.BonusFact fact : result.bonusFacts()) {
            details = line(details, "<gray>" + escape(fact.key()) + ": <white>+" + fact.points()
                    + "</white> " + escape(fact.detail()) + "</gray>");
        }
        return line(details, "<gray>Total bonus: <white>" + result.bonus() + "</white></gray>");
    }

    private static Component line(Component current, String miniMessage) {
        return current.append(Component.newline()).append(MiniMessageUtils.miniMessage(miniMessage));
    }

    private static String rankColor(DungeonRank rank) {
        return "<" + rankColorName(rank) + ">";
    }

    private static String rankColorName(DungeonRank rank) {
        return switch (rank) {
            case S_PLUS -> "gold";
            case S -> "aqua";
            case A -> "green";
            case B -> "yellow";
            case C -> "red";
            case D -> "dark_red";
        };
    }

    private static String displayRank(DungeonRank rank) {
        return rank == DungeonRank.S_PLUS ? "S+" : rank.name();
    }

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return minutes > 0 ? minutes + "m " + remainder + "s" : remainder + "s";
    }

    private static String escape(String value) {
        return value.replace("<", "\\<").replace(">", "\\>");
    }

    private static Duration requirePositive(String name, Duration duration) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
