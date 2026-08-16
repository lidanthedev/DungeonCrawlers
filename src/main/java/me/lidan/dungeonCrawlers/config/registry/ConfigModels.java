package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.utils.Range;
import org.bukkit.Material;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConfigModels {
    private ConfigModels() {
    }

    public record StatModifiers(Map<StatType, Double> add, Map<StatType, Double> multiply) {
        public StatModifiers {
            add = Map.copyOf(add);
            multiply = Map.copyOf(multiply);
        }

        public static StatModifiers empty() { return new StatModifiers(Map.of(), Map.of()); }
    }

    public record ClassDefinition(String id, String displayName, Material icon, StatModifiers stats) {
        public ClassDefinition {
            Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(icon);
            Objects.requireNonNull(stats);
        }
    }

    public enum BlessingStacking { LEVELS, REPLACE }

    public record BlessingDefinition(String id, String displayName, Material icon, BlessingStacking stacking,
                                     int maxLevel, Range levelRange, StatModifiers perLevel) {
        public BlessingDefinition(String id, String displayName, Material icon, BlessingStacking stacking,
                                  int maxLevel, StatModifiers perLevel) {
            this(id, displayName, icon, stacking, maxLevel, new Range(1, 1), perLevel);
        }

        public BlessingDefinition {
            Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(icon);
            Objects.requireNonNull(stacking); Objects.requireNonNull(levelRange); Objects.requireNonNull(perLevel);
            if (maxLevel < 1 || levelRange.getMin() < 1 || levelRange.getMin() > levelRange.getMax()
                    || levelRange.getMax() > maxLevel) {
                throw new IllegalArgumentException("blessing level range must be within 1..maxLevel");
            }
        }
    }

    public enum RoomType { NORMAL, START, PORTAL, BOSS }
    public enum EncounterCapability { NORMAL, MINIBOSS }

    public record RoomDefinition(String id, RoomType type, Set<EncounterCapability> capabilities,
                                 int minFloor, Integer maxFloor, double weight) {
        public RoomDefinition {
            Objects.requireNonNull(id); Objects.requireNonNull(type);
            capabilities = Set.copyOf(capabilities);
        }
    }

    public record FloorDefinition(String id, int number, String displayName, TemplateRefs templates,
                                  Generation generation, List<String> normalMobs, List<String> minibossMobs,
                                  String bossMob, String encounterId, List<String> allowedClasses,
                                  List<WeightedId> blessings, Map<String, RewardDefinition> rewards, Limits limits) {
        public FloorDefinition {
            Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(templates);
            Objects.requireNonNull(generation); Objects.requireNonNull(bossMob); Objects.requireNonNull(encounterId);
            Objects.requireNonNull(limits);
            normalMobs = List.copyOf(normalMobs); minibossMobs = List.copyOf(minibossMobs);
            allowedClasses = List.copyOf(allowedClasses); blessings = List.copyOf(blessings);
            rewards = Map.copyOf(rewards);
        }
    }

    public record TemplateRefs(String start, String portal, String boss, Vector3i bossOffset) { }
    public record Vector3i(int x, int y, int z) { }
    public record Generation(int rooms, int minibosses, boolean finalMiniboss,
                             int maxAttemptsPerPosition, int collisionPadding) { }
    public record WeightedId(String id, double weight) { }
    public record RewardDefinition(boolean enabled, long price, int minScore, int rolls, boolean unique,
                                   List<RewardItem> items) {
        public RewardDefinition { items = List.copyOf(items); }
    }
    public record RewardItem(String itemId, double weight, int minimumAmount, int maximumAmount) { }
    public record Limits(int maxPartySize, int maxTemplateDimension, long maxTemplateVolume,
                         int maxLoadedChunksPerInstance, int mobRespawnRetries, int repositoryQueueCapacity) { }

    public record ConfigSnapshot(int schemaVersion, Map<String, FloorDefinition> floors,
                                 Map<String, RoomDefinition> rooms, Map<String, ClassDefinition> classes,
                                 Map<String, BlessingDefinition> blessings, Set<String> encounters,
                                 String hash, Instant loadedAt) {
        public ConfigSnapshot {
            floors = Map.copyOf(floors); rooms = Map.copyOf(rooms); classes = Map.copyOf(classes);
            blessings = Map.copyOf(blessings); encounters = Set.copyOf(encounters);
            Objects.requireNonNull(hash); Objects.requireNonNull(loadedAt);
        }
    }
}
