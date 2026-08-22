package me.lidan.dungeonCrawlers.core.reward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RewardDefinition;
import me.lidan.dungeonCrawlers.core.claim.OfferMode;
import me.lidan.dungeonCrawlers.core.random.NamedRandomFactory;
import me.lidan.dungeonCrawlers.core.score.ScoreService;
import me.lidan.dungeonCrawlers.persistence.DurableRecord;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.DurableSubmission;
import me.lidan.dungeonCrawlers.persistence.DurableWrite;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Free, claim-free reward access for a completed run.
 *
 * <p>The service freezes the eligible participant set and every roll at registration. Reopening
 * an offer, reconnecting, or previewing it never evaluates a reward definition or RNG stream a
 * second time. Delivery and payment deliberately live in the next phase.</p>
 */
public final class RewardEntitlementService {
    public static final Duration LIVE_WINDOW = Duration.ofMinutes(5);
    public static final Duration RECOVERED_WINDOW = Duration.ofHours(24);
    public static final Duration RECOVERED_SESSION_WINDOW = Duration.ofMinutes(5);

    private static final String REPOSITORY_NAMESPACE = "reward-entitlements";
    private static final int REPOSITORY_SCHEMA_VERSION = 1;

    private final Clock clock;
    private final RewardCatalog catalog;
    private final RewardRoller roller;
    private final DurableRepository repository;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .registerTypeAdapter(Duration.class, new DurationAdapter()).create();
    private final Map<UUID, RunState> runs = new LinkedHashMap<>();

    public RewardEntitlementService(Clock clock, RewardCatalog catalog) {
        this(clock, catalog, new RewardRoller(), null);
    }

    public RewardEntitlementService(Clock clock, RewardCatalog catalog, DurableRepository repository) {
        this(clock, catalog, new RewardRoller(), repository);
    }

    RewardEntitlementService(Clock clock, RewardCatalog catalog, RewardRoller roller) {
        this(clock, catalog, roller, null);
    }

    RewardEntitlementService(Clock clock, RewardCatalog catalog, RewardRoller roller,
                             DurableRepository repository) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.roller = Objects.requireNonNull(roller, "roller");
        this.repository = repository;
        restore();
    }

    /** Registers a completion once. Re-registering the same instance returns its original rolls. */
    public synchronized RunSnapshot register(Completion completion) {
        Objects.requireNonNull(completion, "completion");
        RunState existing = runs.get(completion.instanceId());
        if (existing != null) return existing.snapshot();
        Map<UUID, PlayerEntitlement> players = new LinkedHashMap<>();
        if (completion.score().successful()) {
            for (Participant participant : completion.participants()) {
                if (!participant.activeAtCompletion()) continue;
                OfferMode mode = participant.onlineAtCompletion() ? OfferMode.LIVE : OfferMode.RECOVERED;
                Instant outerDeadline = completion.completedAt().plus(
                        mode == OfferMode.LIVE ? LIVE_WINDOW : RECOVERED_WINDOW);
                Instant sessionStarted = mode == OfferMode.LIVE ? completion.completedAt() : null;
                Instant sessionExpires = mode == OfferMode.LIVE ? outerDeadline : null;
                Map<String, RewardOffer> offers = rollOffers(completion, participant.playerId());
                players.put(participant.playerId(), new PlayerEntitlement(participant.playerId(), mode,
                        outerDeadline, sessionStarted, sessionExpires, offers));
            }
        }
        RunState state = new RunState(completion, players, 1);
        runs.put(completion.instanceId(), state);
        try {
            persist(state);
        } catch (RuntimeException exception) {
            runs.remove(completion.instanceId());
            throw exception;
        }
        return state.snapshot();
    }

    public synchronized Optional<RunSnapshot> info(UUID instanceId) {
        RunState state = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        return state == null ? Optional.empty() : Optional.of(state.snapshot());
    }

    public synchronized Optional<PlayerEntitlement> player(UUID instanceId, UUID playerId) {
        RunState state = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return Optional.empty();
        return Optional.ofNullable(state.players.get(Objects.requireNonNull(playerId, "playerId")));
    }

    /** Opens the reward view. Recovered offers begin their fresh five-minute session here. */
    public synchronized Optional<PlayerEntitlement> open(UUID instanceId, UUID playerId) {
        return reconnect(instanceId, playerId);
    }

    public synchronized Optional<PlayerEntitlement> reconnect(UUID instanceId, UUID playerId) {
        RunState state = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return Optional.empty();
        UUID checkedPlayer = Objects.requireNonNull(playerId, "playerId");
        PlayerEntitlement current = state.players.get(checkedPlayer);
        Instant now = effectiveNow(clock.instant());
        if (current == null) return Optional.empty();
        if (current.mode() == OfferMode.LIVE) {
            return current.open(now) ? Optional.of(current) : Optional.empty();
        }
        if (!now.isBefore(current.outerDeadline())) return Optional.empty();
        if (current.sessionExpiresAt() != null && now.isBefore(current.sessionExpiresAt())) {
            return Optional.of(current);
        }
        PlayerEntitlement recovered = current.startRecoveredSession(now);
        state.players.put(checkedPlayer, recovered);
        state.recordVersion++;
        try {
            persist(state);
        } catch (RuntimeException exception) {
            state.players.put(checkedPlayer, current);
            state.recordVersion--;
            throw exception;
        }
        return Optional.of(recovered);
    }

    public synchronized Optional<RewardOffer> preview(UUID instanceId, UUID playerId, String rewardId) {
        Objects.requireNonNull(rewardId, "rewardId");
        PlayerEntitlement entitlement = player(instanceId, playerId).orElse(null);
        if (entitlement == null || !entitlement.open(effectiveNow(clock.instant()))) return Optional.empty();
        return Optional.ofNullable(entitlement.offers().get(rewardId));
    }

    /** Test/admin reset only; normal completion state is never replaced by a reopen. */
    public synchronized boolean resetTest(UUID instanceId) {
        UUID id = Objects.requireNonNull(instanceId, "instanceId");
        RunState removed = runs.remove(id);
        if (removed == null) return false;
        try {
            deletePersisted(id);
        } catch (RuntimeException exception) {
            runs.put(id, removed);
            throw exception;
        }
        return true;
    }

    public synchronized List<UUID> instances() {
        return List.copyOf(runs.keySet());
    }

    private Map<String, RewardOffer> rollOffers(Completion completion, UUID playerId) {
        Map<String, RewardOffer> offers = new LinkedHashMap<>();
        completion.rewards().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String rewardId = entry.getKey();
            RewardDefinition definition = entry.getValue();
            if (rewardId == null || rewardId.isBlank() || definition == null || !definition.enabled()
                    || definition.items().isEmpty()
                    || definition.items().stream().anyMatch(item -> item == null || !catalog.isConfigured(item.itemId()))) {
                return;
            }
            UUID offerId = UUID.nameUUIDFromBytes((completion.instanceId() + ":" + playerId + ":" + rewardId)
                    .getBytes(StandardCharsets.UTF_8));
            boolean locked = completion.score().total() < definition.minScore();
            List<RewardRoller.RolledReward> rolls = locked ? List.of() : roller.roll(definition,
                    new NamedRandomFactory(completion.seed()).stream(rewardStreamKey(playerId, rewardId)));
            offers.put(rewardId, new RewardOffer(offerId, rewardId, definition.price(), definition.minScore(),
                    locked, rolls));
        });
        return Map.copyOf(offers);
    }

    private void restore() {
        if (repository == null) return;
        try {
            List<DurableRecord> records = repository.list(REPOSITORY_NAMESPACE).join();
            records.stream().sorted(Comparator.comparing(DurableRecord::recordId)).forEach(record -> {
                PersistedRun persisted = decode(record.payload());
                if (!record.recordId().equals(persisted.completion().instanceId().toString())) {
                    throw new IllegalStateException("reward entitlement record ID does not match instance ID");
                }
                runs.put(persisted.completion().instanceId(),
                        new RunState(persisted.completion(), persisted.players(), persisted.recordVersion()));
            });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to restore reward entitlements", unwrap(exception));
        }
    }

    private void persist(RunState state) {
        if (repository == null) return;
        UUID instanceId = state.completion.instanceId();
        DurableWrite write = new DurableWrite(UUID.randomUUID(), instanceId, REPOSITORY_NAMESPACE,
                instanceId.toString(), "reward-entitlement-" + instanceId + "-v" + state.recordVersion,
                state.recordVersion, encode(state));
        DurableSubmission submission = repository.submit(write);
        if (!submission.accepted()) {
            throw new IllegalStateException("Unable to persist reward entitlements: " + submission.detail());
        }
        try {
            submission.receipt().join();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to persist reward entitlements", unwrap(exception));
        }
    }

    private void deletePersisted(UUID instanceId) {
        if (repository == null) return;
        try {
            repository.delete(REPOSITORY_NAMESPACE, instanceId.toString()).join();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to delete reward entitlements", unwrap(exception));
        }
    }

    private byte[] encode(RunState state) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("schema-version", REPOSITORY_SCHEMA_VERSION);
        envelope.add("run", gson.toJsonTree(new PersistedRun(state.completion, state.players,
                state.recordVersion)));
        return gson.toJson(envelope).getBytes(StandardCharsets.UTF_8);
    }

    private PersistedRun decode(byte[] payload) {
        try {
            JsonElement parsed = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new JsonParseException("reward entitlement must be an object");
            JsonObject envelope = parsed.getAsJsonObject();
            JsonElement version = envelope.get("schema-version");
            if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                    || version.getAsBigDecimal().intValueExact() != REPOSITORY_SCHEMA_VERSION) {
                throw new JsonParseException("unsupported reward entitlement schema-version");
            }
            JsonElement encoded = envelope.get("run");
            if (encoded == null || encoded.isJsonNull() || !encoded.isJsonObject()) {
                throw new JsonParseException("reward entitlement run is missing");
            }
            PersistedRun result = gson.fromJson(encoded, PersistedRun.class);
            if (result == null || result.completion() == null || result.players() == null
                    || result.recordVersion() < 1) {
                throw new JsonParseException("invalid reward entitlement run");
            }
            return result;
        } catch (JsonParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JsonParseException("invalid reward entitlement", exception);
        }
    }

    private static Throwable unwrap(RuntimeException exception) {
        if (exception.getCause() instanceof RuntimeException cause) return cause;
        return exception;
    }

    private Instant effectiveNow(Instant now) {
        return now;
    }

    static String rewardStreamKey(UUID playerId, String rewardId) {
        return "reward:" + playerId + ":" + rewardId;
    }

    @FunctionalInterface
    public interface RewardCatalog {
        boolean isConfigured(String itemId);
    }

    public record Completion(UUID instanceId, long seed, Instant completedAt,
                             ScoreService.FinalScoreSnapshot score, List<Participant> participants,
                             Map<String, RewardDefinition> rewards) {
        public Completion {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(completedAt, "completedAt");
            Objects.requireNonNull(score, "score");
            Objects.requireNonNull(participants, "participants");
            Objects.requireNonNull(rewards, "rewards");
            participants = List.copyOf(participants);
            rewards = Map.copyOf(rewards);
            Set<UUID> ids = new HashSet<>();
            for (Participant participant : participants) {
                Objects.requireNonNull(participant, "participant");
                if (!ids.add(participant.playerId())) throw new IllegalArgumentException("duplicate participant");
            }
        }
    }

    public record Participant(UUID playerId, boolean activeAtCompletion, boolean onlineAtCompletion) {
        public Participant {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    public record RewardOffer(UUID offerId, String rewardId, long price, int minScore, boolean locked,
                              List<RewardRoller.RolledReward> rolls) {
        public RewardOffer(UUID offerId, String rewardId, long price, boolean locked,
                           List<RewardRoller.RolledReward> rolls) {
            this(offerId, rewardId, price, 0, locked, rolls);
        }

        public RewardOffer {
            Objects.requireNonNull(offerId, "offerId");
            Objects.requireNonNull(rewardId, "rewardId");
            if (rewardId.isBlank() || price < 0 || minScore < 0) {
                throw new IllegalArgumentException("invalid reward offer");
            }
            rolls = List.copyOf(rolls);
            if (locked && !rolls.isEmpty()) throw new IllegalArgumentException("locked offer cannot have rolls");
        }

        public List<RewardRoller.RolledReward> legalStacks(int maxStackSize) {
            return new RewardStackSplitter().splitAll(rolls, maxStackSize);
        }
    }

    public record PlayerEntitlement(UUID playerId, OfferMode mode, Instant outerDeadline,
                                    Instant sessionStartedAt, Instant sessionExpiresAt,
                                    Map<String, RewardOffer> offers) {
        public PlayerEntitlement {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(outerDeadline, "outerDeadline");
            offers = Map.copyOf(offers);
            if (sessionStartedAt != null && sessionExpiresAt == null
                    || sessionStartedAt == null && sessionExpiresAt != null) {
                throw new IllegalArgumentException("session timestamps must be paired");
            }
            if (sessionStartedAt != null && !sessionExpiresAt.isAfter(sessionStartedAt)) {
                throw new IllegalArgumentException("session expiry must be after session start");
            }
        }

        public boolean open(Instant now) {
            Objects.requireNonNull(now, "now");
            return now.isBefore(outerDeadline)
                    && (sessionExpiresAt == null || now.isBefore(sessionExpiresAt));
        }

        private PlayerEntitlement startRecoveredSession(Instant now) {
            return new PlayerEntitlement(playerId, mode, outerDeadline, now,
                    now.plus(RECOVERED_SESSION_WINDOW), offers);
        }
    }

    public record RunSnapshot(UUID instanceId, Instant completedAt, ScoreService.FinalScoreSnapshot score,
                              Map<UUID, PlayerEntitlement> players) {
        public RunSnapshot {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(completedAt, "completedAt");
            Objects.requireNonNull(score, "score");
            players = Map.copyOf(players);
        }
    }

    private record PersistedRun(Completion completion, Map<UUID, PlayerEntitlement> players,
                                long recordVersion) { }

    private static final class RunState {
        private final Completion completion;
        private final Map<UUID, PlayerEntitlement> players;
        private long recordVersion;

        private RunState(Completion completion, Map<UUID, PlayerEntitlement> players, long recordVersion) {
            if (recordVersion < 1) throw new IllegalArgumentException("record version must be positive");
            this.completion = completion;
            this.players = new LinkedHashMap<>(players);
            this.recordVersion = recordVersion;
        }

        private RunSnapshot snapshot() {
            return new RunSnapshot(completion.instanceId(), completion.completedAt(), completion.score(), players);
        }
    }

    private static final class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant source, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(source.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            try {
                return Instant.parse(json.getAsString());
            } catch (RuntimeException exception) {
                throw new JsonParseException("invalid reward entitlement instant", exception);
            }
        }
    }

    private static final class DurationAdapter implements JsonSerializer<Duration>, JsonDeserializer<Duration> {
        @Override
        public JsonElement serialize(Duration source, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(source.toString());
        }

        @Override
        public Duration deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            try {
                return Duration.parse(json.getAsString());
            } catch (RuntimeException exception) {
                throw new JsonParseException("invalid reward entitlement duration", exception);
            }
        }
    }
}
