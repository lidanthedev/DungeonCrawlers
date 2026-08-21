package me.lidan.dungeonCrawlers.core.score;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreResultRendererTest {
    @Test
    void resultHasMultilineCategoryAndBonusHoverDetails() {
        ScoreService.ScoreReport report = new ScoreService().calculateReport(
                new ScoreService.ScoreInput(true, 2, Duration.ofMinutes(20), 11, 12),
                List.of(new ScoreService.BonusProvider() {
                    @Override public String id() { return "no-deaths"; }
                    @Override public int priority() { return 0; }
                    @Override public List<ScoreService.BonusFact> evaluate(ScoreService.ScoreSnapshot snapshot) {
                        return List.of(new ScoreService.BonusFact("no_deaths", 10, "No deaths"));
                    }
                }));

        var rendered = ScoreResultRenderer.render(report);
        String visible = PlainTextComponentSerializer.plainText().serialize(rendered);
        String hover = PlainTextComponentSerializer.plainText().serialize((Component) rendered.hoverEvent().value());

        assertTrue(visible.contains("274"), visible);
        assertTrue(hover.contains("Skill: 96"), hover);
        assertTrue(hover.contains("Time: 76"), hover);
        assertTrue(hover.contains("Exploration: 92"), hover);
        assertTrue(hover.contains("no_deaths: +10 No deaths"), hover);

        var max = new ScoreService().calculateReport(
                new ScoreService.ScoreInput(true, 0, Duration.ofMinutes(8), 0, 0), List.of());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(ScoreResultRenderer.render(max))
                .contains("(S+)"));
    }

    @Test
    void failedSnapshotRendersFailureWithoutChangingCategoryValues() {
        var report = new ScoreService().calculateReport(
                new ScoreService.ScoreInput(false, 0, Duration.ofMinutes(8), 0, 0), List.of());

        String visible = PlainTextComponentSerializer.plainText().serialize(ScoreResultRenderer.render(report));
        String hover = PlainTextComponentSerializer.plainText().serialize(
                (Component) ScoreResultRenderer.render(report).hoverEvent().value());

        assertTrue(visible.startsWith("[FAIL]"), visible);
        assertTrue(hover.contains("Skill: 0"), hover);
    }
}
