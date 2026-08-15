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
import java.util.logging.Logger;

public final class TemplateCatalogLoader {
    private final TemplateAuthoringService authoring;
    private final WorldEditGateway worldEdit;
    private final TemplateValidator validator;
    private final EmeraldPolicy emeraldPolicy;
    private final Logger logger = Logger.getLogger(TemplateCatalogLoader.class.getName());
    private CacheEntry cache;

    public TemplateCatalogLoader(TemplateAuthoringService authoring, WorldEditGateway worldEdit,
                                 TemplateValidator validator, EmeraldPolicy emeraldPolicy) {
        this.authoring = Objects.requireNonNull(authoring);
        this.worldEdit = Objects.requireNonNull(worldEdit);
        this.validator = Objects.requireNonNull(validator);
        this.emeraldPolicy = Objects.requireNonNull(emeraldPolicy);
    }

    public synchronized LoadResult load(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        int maximumDimension = snapshot.floors().values().stream()
                .mapToInt(floor -> floor.limits().maxTemplateDimension()).max().orElse(512);
        long maximumVolume = snapshot.floors().values().stream()
                .mapToLong(floor -> floor.limits().maxTemplateVolume()).max().orElse(16_777_216L);
        Map<String, FileStamp> fingerprints = new LinkedHashMap<>();
        Map<String, FileStamp> metadataFingerprints = new LinkedHashMap<>();
        boolean cacheable = true;
        for (String id : snapshot.rooms().keySet().stream().sorted().toList()) {
            FileStamp fingerprint = fingerprint(authoring.schematicPath(id));
            if (fingerprint == null) cacheable = false;
            else fingerprints.put(id, fingerprint);
            FileStamp metadataFingerprint = fingerprint(authoring.metadataPath(id));
            if (metadataFingerprint == null) cacheable = false;
            else metadataFingerprints.put(id, metadataFingerprint);
        }
        CacheKey key = new CacheKey(snapshot.hash(), maximumDimension, maximumVolume, fingerprints,
                metadataFingerprints);
        if (cacheable && cache != null && cache.key().equals(key)) return cache.result();

        Map<String, CatalogEntry> catalog = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        snapshot.rooms().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String id = entry.getKey();
            try {
                if (!Files.isRegularFile(authoring.schematicPath(id))) {
                    errors.add(id + ": schematic is missing");
                    return;
                }
                Optional<TemplateMetadata> metadata;
                try {
                    metadata = authoring.metadata(id);
                } catch (IOException | RuntimeException exception) {
                    String detail = exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage();
                    logger.warning(id + ": metadata lookup failed: " + detail + "; rescanning schematic");
                    metadata = Optional.empty();
                }
                FileStamp schematicStamp = fingerprints.get(id);
                if (metadata.isPresent() && schematicStamp != null && metadataMatches(metadata.orElseThrow(), schematicStamp)) {
                    catalog.put(id, new CatalogEntry(entry.getValue(),
                            metadata.orElseThrow().toTemplate(id, entry.getValue())));
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
                var fullTemplate = validated.template().orElseThrow();
                try {
                    authoring.writeMetadata(id, fullTemplate);
                } catch (IOException ignored) {
                    // The validated schematic remains usable; the next load will retry metadata migration.
                }
                catalog.put(id, new CatalogEntry(entry.getValue(), fullTemplate.withoutSolidBlocks()));
            } catch (IOException | RuntimeException exception) {
                errors.add(id + ": " + (exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage()));
            }
        });
        LoadResult result = errors.isEmpty() ? new LoadResult(Optional.of(Map.copyOf(catalog)), List.of())
                : new LoadResult(Optional.empty(), errors);
        if (cacheable && result.successful()) cache = new CacheEntry(key, result);
        return result;
    }

    private static FileStamp fingerprint(java.nio.file.Path path) {
        try {
            if (!Files.isRegularFile(path)) return new FileStamp(false, -1L, -1L);
            return new FileStamp(true, Files.size(path), Files.getLastModifiedTime(path).toMillis());
        } catch (IOException exception) {
            return null;
        }
    }

    private static boolean metadataMatches(TemplateMetadata metadata, FileStamp schematic) {
        return metadata.schematicSize() == schematic.size()
                && metadata.schematicModifiedMillis() == schematic.modifiedMillis();
    }

    private record FileStamp(boolean regularFile, long size, long modifiedMillis) { }

    private record CacheKey(String configHash, int maximumDimension, long maximumVolume,
                            Map<String, FileStamp> fingerprints, Map<String, FileStamp> metadataFingerprints) {
        private CacheKey {
            fingerprints = Map.copyOf(fingerprints);
            metadataFingerprints = Map.copyOf(metadataFingerprints);
        }
    }

    private record CacheEntry(CacheKey key, LoadResult result) { }

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
