package me.lidan.dungeonCrawlers.core.score;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.Objects;

/** Builds the player-facing score line while keeping category details in a hover. */
public final class ScoreResultRenderer {
    private ScoreResultRenderer() { }

    public static Component render(ScoreService.ScoreReport report) {
        Objects.requireNonNull(report, "report");
        return render(report.result(), report.snapshot().successful());
    }

    public static Component render(ScoreService.ScoreResult result) {
        Objects.requireNonNull(result, "result");
        return render(result, true);
    }

    private static Component render(ScoreService.ScoreResult result, boolean successful) {
        String color = successful ? "green" : "red";
        String status = successful ? "PASS" : "FAIL";
        Component visible = MiniMessageUtils.miniMessage("<" + color + ">[" + status
                + "] Dungeon score: <white>" + result.total() + "</white> (<aqua>"
                + displayRank(result.rank()) + "</aqua>)</" + color + ">");
        return visible.hoverEvent(HoverEvent.showText(details(result)));
    }

    private static Component details(ScoreService.ScoreResult result) {
        Component details = MiniMessageUtils.miniMessage("<gray><bold>Score details</bold></gray>");
        details = line(details, "<gray>Skill: <white>" + result.skill() + "</white></gray>");
        details = line(details, "<gray>Time: <white>" + result.time() + "</white></gray>");
        details = line(details, "<gray>Exploration: <white>" + result.exploration() + "</white></gray>");
        details = line(details, "<gray>Bonus: <white>" + result.bonus() + "</white></gray>");
        details = line(details, "<gray>Total: <white>" + result.total() + "</white></gray>");
        details = line(details, "<gray>Rank: <aqua>" + displayRank(result.rank()) + "</aqua></gray>");
        for (ScoreService.BonusFact fact : result.bonusFacts()) {
            Component prefix = MiniMessageUtils.miniMessage("<yellow>" + escape(fact.key())
                    + ": +" + fact.points() + "</yellow> ");
            details = details.append(Component.newline()).append(prefix).append(Component.text(fact.detail()));
        }
        return details;
    }

    private static Component line(Component current, String miniMessage) {
        return current.append(Component.newline()).append(MiniMessageUtils.miniMessage(miniMessage));
    }

    private static String escape(String value) {
        return value.replace("<", "\\<").replace(">", "\\>");
    }

    private static String displayRank(DungeonRank rank) {
        return rank == DungeonRank.S_PLUS ? "S+" : rank.name();
    }
}
