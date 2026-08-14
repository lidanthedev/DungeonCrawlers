package me.lidan.dungeonCrawlers.core.generation;

import me.lidan.dungeonCrawlers.authoring.TemplateAuthoringService;
import me.lidan.dungeonCrawlers.authoring.TemplateCatalogLoader;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.core.generation.SlotAllocator.SlotLease;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class GenerationPreparationProvider implements GenerationService.PreparationProvider {
    private final TemplateCatalogLoader catalogLoader;
    private final TemplateAuthoringService authoring;
    private final LayoutPlanner planner;

    public GenerationPreparationProvider(TemplateCatalogLoader catalogLoader, TemplateAuthoringService authoring,
                                         LayoutPlanner planner) {
        this.catalogLoader = Objects.requireNonNull(catalogLoader);
        this.authoring = Objects.requireNonNull(authoring);
        this.planner = Objects.requireNonNull(planner);
    }

    @Override
    public GenerationService.PreparedGeneration prepare(UUID instanceId, long seed, FloorDefinition floor,
                                                        ConfigSnapshot snapshot, SlotLease slot) throws Exception {
        TemplateCatalogLoader.LoadResult loaded = catalogLoader.load(snapshot);
        if (!loaded.successful()) throw new IllegalArgumentException(String.join("; ", loaded.errors()));
        var result = planner.plan(new LayoutPlanner.PlanRequest(instanceId, seed, floor,
                loaded.catalog().orElseThrow(), slot.origin(), slot.usableBounds(), snapshot.hash()));
        if (!result.successful()) throw new IllegalArgumentException(String.join("; ", result.errors()));
        LayoutPlanner.LayoutPlan plan = result.plan().orElseThrow();
        Map<String, byte[]> schematics = new LinkedHashMap<>();
        for (var placement : plan.placements()) {
            schematics.computeIfAbsent(placement.templateId(), id -> {
                try {
                    return authoring.schematic(id);
                } catch (Exception exception) {
                    throw new SchematicReadException(id, exception);
                }
            });
        }
        return new GenerationService.PreparedGeneration(plan, schematics);
    }

    private static final class SchematicReadException extends RuntimeException {
        private SchematicReadException(String templateId, Throwable cause) {
            super("schematic read failed for " + templateId + ": "
                    + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()), cause);
        }
    }
}
