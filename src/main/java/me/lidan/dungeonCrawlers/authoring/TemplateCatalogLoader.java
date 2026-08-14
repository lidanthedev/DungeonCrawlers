package me.lidan.dungeonCrawlers.authoring;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.CatalogEntry;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.EmeraldPolicy;
import me.lidan.dungeonCrawlers.core.template.TemplateValidator;
import me.lidan.dungeonCrawlers.integration.WorldEditGateway;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TemplateCatalogLoader {
    private final TemplateAuthoringService authoring;
    private final WorldEditGateway worldEdit;
    private final TemplateValidator validator;
    private final EmeraldPolicy emeraldPolicy;

    public TemplateCatalogLoader(TemplateAuthoringService authoring, WorldEditGateway worldEdit,
                                 TemplateValidator validator, EmeraldPolicy emeraldPolicy) {
        this.authoring = Objects.requireNonNull(authoring);
        this.worldEdit = Objects.requireNonNull(worldEdit);
        this.validator = Objects.requireNonNull(validator);
        this.emeraldPolicy = Objects.requireNonNull(emeraldPolicy);
    }

    public LoadResult load(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        int maximumDimension = snapshot.floors().values().stream()
                .mapToInt(floor -> floor.limits().maxTemplateDimension()).max().orElse(512);
        long maximumVolume = snapshot.floors().values().stream()
                .mapToLong(floor -> floor.limits().maxTemplateVolume()).max().orElse(16_777_216L);
        Map<String, CatalogEntry> catalog = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        snapshot.rooms().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String id = entry.getKey();
            try {
                if (!Files.isRegularFile(authoring.schematicPath(id))) {
                    errors.add(id + ": schematic is missing");
                    return;
                }
                WorldEditGateway.ScanResult scanned = worldEdit.read(authoring.schematic(id),
                        maximumDimension, maximumVolume);
                if (!scanned.successful()) {
                    errors.add(id + ": " + scanned.detail());
                    return;
                }
                var validated = validator.validate(id, entry.getValue().type(), entry.getValue().capabilities(),
                        scanned.selection().orElseThrow(), emeraldPolicy);
                if (!validated.successful()) {
                    validated.errors().forEach(error -> errors.add(id + ": " + error));
                    return;
                }
                catalog.put(id, new CatalogEntry(entry.getValue(), validated.template().orElseThrow()));
            } catch (IOException | RuntimeException exception) {
                errors.add(id + ": " + (exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage()));
            }
        });
        return errors.isEmpty() ? new LoadResult(Optional.of(Map.copyOf(catalog)), List.of())
                : new LoadResult(Optional.empty(), errors);
    }

    public record LoadResult(Optional<Map<String, CatalogEntry>> catalog, List<String> errors) {
        public LoadResult {
            Objects.requireNonNull(catalog); errors = List.copyOf(errors);
            if (catalog.isPresent() == !errors.isEmpty()) {
                throw new IllegalArgumentException("a catalog result must contain either a catalog or errors");
            }
        }
        public boolean successful() { return catalog.isPresent(); }
    }
}
