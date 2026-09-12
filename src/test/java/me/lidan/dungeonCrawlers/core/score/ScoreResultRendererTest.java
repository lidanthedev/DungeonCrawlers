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
        String hover = hoverText(rendered);

        assertTrue(visible.contains("274"), visible);
        assertTrue(hover.contains("Final: 96"), hover);
        assertTrue(hover.contains("Final: 76"), hover);
        assertTrue(hover.contains("Final: 92"), hover);
        assertTrue(hover.contains("no_deaths: +10 No deaths"), hover);

        var max = new ScoreService().calculateReport(
                new ScoreService.ScoreInput(true, 0, Duration.ofMinutes(8), 0, 0), List.of());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(ScoreResultRenderer.render(max))
                .contains("Rank: S+"));
    }

    @Test
    void failedSnapshotRendersFailureWithoutChangingCategoryValues() {
        var report = new ScoreService().calculateReport(
                new ScoreService.ScoreInput(false, 0, Duration.ofMinutes(8), 0, 0), List.of());

        String visible = PlainTextComponentSerializer.plainText().serialize(ScoreResultRenderer.render(report));
        String hover = hoverText(ScoreResultRenderer.render(report));

        assertTrue(visible.startsWith("Dungeon Failed!"), visible);
        assertTrue(visible.contains("Skill: 0/100"), visible);
        assertTrue(hover.contains("The run failed, so skill is forced to 0."), hover);
    }

    private static String hoverText(Component component) {
        StringBuilder text = new StringBuilder();
        if (component.hoverEvent() != null) {
            text.append(PlainTextComponentSerializer.plainText().serialize(
                    (Component) component.hoverEvent().value())).append('\n');
        }
        component.children().forEach(child -> text.append(hoverText(child)));
        return text.toString();
    }
}
