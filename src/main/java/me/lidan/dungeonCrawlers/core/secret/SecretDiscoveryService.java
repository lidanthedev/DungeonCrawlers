package me.lidan.dungeonCrawlers.core.secret;

import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.WeightedId;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.LayoutPlan;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.PlacedSecret;
import me.lidan.dungeonCrawlers.core.location.LocationContextService;
import me.lidan.dungeonCrawlers.core.random.NamedRandomFactory;
import me.lidan.dungeonCrawlers.core.random.WeightedChooser;
import me.lidan.dungeonCrawlers.core.stats.BlessingLevels;
import me.lidan.dungeonCrawlers.core.stats.StatAggregationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Owns one-shot secret discovery and the party-wide transient blessing state. */
public final class SecretDiscoveryService {
    private final Supplier<ConfigSnapshot> config;
    private final StatAggregationService stats;
    private final LocationContextService locations = new LocationContextService();
    private final Map<UUID, MutableInstance> instances = new LinkedHashMap<>();

    public SecretDiscoveryService(Supplier<ConfigSnapshot> config) {
        this(config, new StatAggregationService());
    }

    public SecretDiscoveryService(Supplier<ConfigSnapshot> config, StatAggregationService stats) {
        this.config = Objects.requireNonNull(config, "config");
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    public synchronized RegistrationResult register(UUID instanceId, long seed, FloorDefinition floor,
                                                     LayoutPlan plan, Set<UUID> participants) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(participants, "participants");
        if (!instanceId.equals(plan.instanceId())) return RegistrationResult.failure("layout instance mismatch");
        if (instances.containsKey(instanceId)) return RegistrationResult.failure("secret state already registered");
        ConfigSnapshot snapshot = Objects.requireNonNull(config.get(), "config snapshot");
        Map<String, BlessingDefinition> definitions = snapshot.blessings();
        for (WeightedId blessing : floor.blessings()) {
            if (!definitions.containsKey(blessing.id())) {
                return RegistrationResult.failure("unknown floor blessing " + blessing.id());
            }
        }
        LocationContextService.RegistrationResult location = locations.register(plan);
        if (!location.successful()) return RegistrationResult.failure(location.detail());
        List<PlacedSecret> secrets = plan.placements().stream()
                .flatMap(placement -> placement.secrets().stream())
                .sorted(Comparator.comparing(PlacedSecret::worldPoint)
                        .thenComparing(value -> value.id().toString()))
                .toList();
        Set<SecretId> ids = new LinkedHashSet<>();
        Set<Point> points = new LinkedHashSet<>();
        for (PlacedSecret secret : secrets) {
            if (!ids.add(secret.id())) {
                locations.unregister(instanceId);
                return RegistrationResult.failure("duplicate secret id " + secret.id());
            }
            if (!points.add(secret.worldPoint())) {
                locations.unregister(instanceId);
                return RegistrationResult.failure("duplicate secret location " + secret.worldPoint());
            }
        }
        MutableInstance state = new MutableInstance(instanceId, seed, floor, Set.copyOf(participants),
                definitions, secrets);
        instances.put(instanceId, state);
        return RegistrationResult.success("secret state registered", snapshot(state));
    }

    public synchronized DiscoveryResult discover(UUID instanceId, UUID playerId, Point point) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(point, "point");
        MutableInstance state = instances.get(instanceId);
        if (state == null) return DiscoveryResult.failure("unknown secret instance");
        if (!state.participants.contains(playerId)) return DiscoveryResult.failure("player is not in this run");
        return discover(state, playerId, point);
    }

    /** Explicit admin probe; unlike player discovery it may inspect a non-participant's secret. */
    public synchronized DiscoveryResult adminDiscover(UUID instanceId, UUID operatorId, Point point) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(point, "point");
        MutableInstance state = instances.get(instanceId);
        if (state == null) return DiscoveryResult.failure("unknown secret instance");
        return discover(state, operatorId, point);
    }

    private DiscoveryResult discover(MutableInstance state, UUID playerId, Point point) {
        MutableSecret secret = state.secrets.stream().filter(value -> value.secret.worldPoint().equals(point))
                .findFirst().orElse(null);
        if (secret == null) return DiscoveryResult.failure("no secret at " + point);
        if (secret.foundBy != null) return DiscoveryResult.alreadyFound(secret.snapshot());
        secret.foundBy = playerId;
        String blessingId = null;
        BlessingLevels.DiscoveryResult blessing = null;
        if (secret.secret.kind() == me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretKind.BLESSING
                && !state.floor.blessings().isEmpty()) {
            WeightedId selected = WeightedChooser.choose(state.floor.blessings().stream()
                    .map(value -> new WeightedChooser.Weighted<>(value, value.weight())).toList(),
                    new NamedRandomFactory(state.seed).stream("blessing:" + secret.secret.id()));
            blessingId = selected.id();
            BlessingDefinition definition = state.definitions.get(blessingId);
            int discoveries = randomDiscoveries(definition, state.seed, secret.secret.id());
            blessing = state.levels.discover(definition, discoveries);
            secret.blessingId = blessingId;
        }
        return DiscoveryResult.discovered(secret.snapshot(), blessingId, blessing);
    }

    private static int randomDiscoveries(BlessingDefinition blessing, long seed, SecretId secretId) {
        var range = blessing.levelRange();
        long maximumExclusive = range.getMax() + 1;
        return (int) new NamedRandomFactory(seed).stream("blessing-level:" + secretId)
                .nextLong(range.getMin(), maximumExclusive);
    }

    public synchronized boolean reset(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return false;
        state.secrets.forEach(secret -> {
            secret.foundBy = null;
            secret.blessingId = null;
        });
        state.levels.clear();
        return true;
    }

    public synchronized Optional<BlessingLevels.DiscoveryResult> addBlessing(UUID instanceId, String blessingId) {
        return addBlessing(instanceId, blessingId, 1);
    }

    public synchronized Optional<BlessingLevels.DiscoveryResult> addBlessing(UUID instanceId, String blessingId,
                                                                               int discoveries) {
        if (discoveries < 1 || discoveries > 1_000) {
            throw new IllegalArgumentException("discoveries must be in 1..1000");
        }
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return Optional.empty();
        BlessingDefinition blessing = state.definitions.get(Objects.requireNonNull(blessingId, "blessingId"));
        if (blessing == null) return Optional.empty();
        BlessingLevels.DiscoveryResult result = null;
        for (int index = 0; index < discoveries; index++) result = state.levels.discover(blessing);
        return Optional.of(result);
    }

    public synchronized boolean removeBlessing(UUID instanceId, String blessingId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        return state != null && state.levels.remove(Objects.requireNonNull(blessingId, "blessingId"));
    }

    public synchronized boolean clearBlessings(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return false;
        state.levels.clear();
        return true;
    }

    public synchronized Map<StatType, Double> aggregate(UUID instanceId, ClassDefinition selectedClass,
                                                          Map<StatType, Double> incoming) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return Map.copyOf(incoming);
        return stats.aggregate(incoming, selectedClass, state.definitions, state.levels.snapshot());
    }

    public synchronized Map<String, Integer> blessingLevels(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        return state == null ? Map.of() : state.levels.snapshot();
    }

    /** Returns the configured display name for a blessing in an active instance. */
    public synchronized Optional<String> blessingDisplayName(UUID instanceId, String blessingId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return Optional.empty();
        BlessingDefinition blessing = state.definitions.get(Objects.requireNonNull(blessingId, "blessingId"));
        return blessing == null ? Optional.empty() : Optional.of(blessing.displayName());
    }

    public synchronized List<SecretSnapshot> secrets(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        return state == null ? List.of() : state.secrets.stream().map(MutableSecret::snapshot).toList();
    }

    public synchronized Optional<InstanceSnapshot> info(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        return state == null ? Optional.empty() : Optional.of(snapshot(state));
    }

    public synchronized Optional<LocationContextService.RoomContext> locate(UUID instanceId, Point point) {
        return locations.locate(Objects.requireNonNull(instanceId, "instanceId"), Objects.requireNonNull(point, "point"));
    }

    public synchronized List<LocationContextService.RoomContext> rooms(UUID instanceId) {
        return locations.rooms(Objects.requireNonNull(instanceId, "instanceId"));
    }

    public synchronized Optional<LocationContextService.RoomContext> roomsForPoint(Point point) {
        Objects.requireNonNull(point, "point");
        return locations.instanceAt(point).flatMap(id -> locations.locate(id, point));
    }

    public synchronized boolean cleanup(UUID instanceId) {
        UUID id = Objects.requireNonNull(instanceId, "instanceId");
        boolean removed = instances.remove(id) != null;
        locations.unregister(id);
        return removed;
    }

    public synchronized void cleanupAll() {
        instances.clear();
        locations.unregisterAll();
    }

    private static InstanceSnapshot snapshot(MutableInstance state) {
        return new InstanceSnapshot(state.instanceId, state.secrets.stream().map(MutableSecret::snapshot).toList(),
                state.levels.snapshot());
    }

    private static final class MutableInstance {
        private final UUID instanceId;
        private final long seed;
        private final FloorDefinition floor;
        private final Set<UUID> participants;
        private final Map<String, BlessingDefinition> definitions;
        private final List<MutableSecret> secrets;
        private final BlessingLevels levels = new BlessingLevels();

        private MutableInstance(UUID instanceId, long seed, FloorDefinition floor, Set<UUID> participants,
                                Map<String, BlessingDefinition> definitions, List<PlacedSecret> secrets) {
            this.instanceId = instanceId;
            this.seed = seed;
            this.floor = floor;
            this.participants = participants;
            this.definitions = Map.copyOf(definitions);
            this.secrets = secrets.stream().map(MutableSecret::new).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
    }

    private static final class MutableSecret {
        private final PlacedSecret secret;
        private UUID foundBy;
        private String blessingId;

        private MutableSecret(PlacedSecret secret) { this.secret = secret; }

        private SecretSnapshot snapshot() {
            return new SecretSnapshot(secret.id(), secret.worldPoint(), secret.kind(), foundBy, blessingId);
        }
    }

    public record SecretSnapshot(SecretId id, Point worldPoint,
                                 me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretKind kind,
                                 UUID foundBy, String blessingId) {
        public SecretSnapshot {
            Objects.requireNonNull(id); Objects.requireNonNull(worldPoint); Objects.requireNonNull(kind);
        }

        public boolean discovered() { return foundBy != null; }
    }

    public record InstanceSnapshot(UUID instanceId, List<SecretSnapshot> secrets,
                                   Map<String, Integer> blessingLevels) {
        public InstanceSnapshot {
            Objects.requireNonNull(instanceId); secrets = List.copyOf(secrets);
            blessingLevels = Map.copyOf(blessingLevels);
        }
    }

    public record DiscoveryResult(Status status, String detail, SecretSnapshot secret,
                                  String blessingId, BlessingLevels.DiscoveryResult blessing) {
        public DiscoveryResult {
            Objects.requireNonNull(status); Objects.requireNonNull(detail);
        }

        public static DiscoveryResult discovered(SecretSnapshot secret, String blessingId,
                                                 BlessingLevels.DiscoveryResult blessing) {
            return new DiscoveryResult(Status.DISCOVERED, "secret discovered", secret, blessingId, blessing);
        }

        public static DiscoveryResult alreadyFound(SecretSnapshot secret) {
            return new DiscoveryResult(Status.ALREADY_DISCOVERED, "secret already discovered", secret, null, null);
        }

        public static DiscoveryResult failure(String detail) {
            return new DiscoveryResult(Status.FAILURE, detail, null, null, null);
        }

        public boolean successful() { return status == Status.DISCOVERED || status == Status.ALREADY_DISCOVERED; }
    }

    public enum Status { DISCOVERED, ALREADY_DISCOVERED, FAILURE }

    public record RegistrationResult(boolean successful, String detail, InstanceSnapshot snapshot) {
        public RegistrationResult { Objects.requireNonNull(detail); }
        public static RegistrationResult success(String detail, InstanceSnapshot snapshot) {
            return new RegistrationResult(true, detail, snapshot);
        }
        public static RegistrationResult failure(String detail) {
            return new RegistrationResult(false, detail, null);
        }
    }
}
