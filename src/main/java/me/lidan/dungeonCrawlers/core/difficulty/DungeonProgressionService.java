package me.lidan.dungeonCrawlers.core.difficulty;

import com.google.gson.Gson;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.DurableWrite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Durable run outcomes are also the source of per-floor unlocks and pending native skill grants. */
public final class DungeonProgressionService {
    private static final String NAMESPACE = "dungeon-outcomes";
    private final Gson gson = new Gson();
    private final DurableRepository repository;
    private final Map<UUID, Outcome> outcomes = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> unlocked = new HashMap<>();
    private final Set<UUID> delivering = new HashSet<>();

    public DungeonProgressionService(DurableRepository repository) {
        this.repository = repository;
        for (var record : repository.list(NAMESPACE).join()) {
            Outcome outcome = gson.fromJson(new String(record.payload(), StandardCharsets.UTF_8), Outcome.class);
            validate(outcome);
            if (!record.recordId().equals(outcome.id().toString())) throw new IllegalStateException("outcome identity mismatch");
            if (!outcome.settled()) {
                outcome = new Outcome(1, outcome.id(), outcome.instance(), outcome.player(), outcome.floor(),
                        outcome.difficulty(), false, outcome.xp(), false, true);
                save(outcome);
            }
            outcomes.put(outcome.id(), outcome);
            unlock(outcome);
        }
    }

    public boolean isUnlocked(UUID player, String floor, Difficulty tier) {
        return unlocked.getOrDefault(player, Map.of()).getOrDefault(floor, 0) >= tier.ordinal();
    }

    public List<UUID> lockedMembers(List<UUID> members, String floor, Difficulty tier) {
        return members.stream().filter(player -> !isUnlocked(player, floor, tier)).toList();
    }

    public void begin(UUID instance, UUID player, FloorDefinition floor, DifficultyRules rules) {
        UUID id = UUID.nameUUIDFromBytes((instance + ":" + player).getBytes(StandardCharsets.UTF_8));
        if (outcomes.containsKey(id)) return;
        Outcome outcome = new Outcome(1, id, instance, player, floor.id(), rules.tier(), false,
                floor.completionXp() * floor.failureXpFactor() * rules.xpMultiplier(), false, false);
        save(outcome);
        outcomes.put(id, outcome);
    }

    public Outcome record(UUID instance, UUID player, FloorDefinition floor, DifficultyRules rules, boolean success) {
        UUID id = UUID.nameUUIDFromBytes((instance + ":" + player).getBytes(StandardCharsets.UTF_8));
        Outcome existing = outcomes.get(id);
        if (existing != null && existing.settled()) return existing;
        double xp = floor.completionXp() * (success ? 1 : floor.failureXpFactor()) * rules.xpMultiplier();
        Outcome outcome = new Outcome(1, id, instance, player, floor.id(), rules.tier(), success, xp, false, true);
        save(outcome);
        outcomes.put(id, outcome);
        unlock(outcome);
        return outcome;
    }

    public void deliver(UUID player, Function<Outcome, CompletableFuture<Boolean>> grant, Consumer<Runnable> mainThread,
                        Consumer<String> errors) {
        for (Outcome outcome : new ArrayList<>(outcomes.values())) {
            if (!outcome.player().equals(player) || outcome.delivered() || !outcome.settled() || !delivering.add(outcome.id())) continue;
            try {
                grant.apply(outcome).whenComplete((accepted, failure) -> mainThread.accept(() -> {
                    delivering.remove(outcome.id());
                    if (failure != null) { errors.accept("Dungeon XP grant failed: " + failure.getMessage()); return; }
                    if (!Boolean.TRUE.equals(accepted)) return;
                    Outcome done = new Outcome(outcome.schemaVersion(), outcome.id(), outcome.instance(), player,
                            outcome.floor(), outcome.difficulty(), outcome.successful(), outcome.xp(), true, true);
                    try { save(done); outcomes.put(done.id(), done); }
                    catch (RuntimeException exception) { errors.accept("Dungeon XP acknowledgment failed: " + exception.getMessage()); }
                }));
            } catch (RuntimeException exception) {
                delivering.remove(outcome.id());
                errors.accept("Dungeon XP grant failed: " + exception.getMessage());
            }
        }
    }

    private void unlock(Outcome outcome) {
        if (outcome.successful()) unlocked.computeIfAbsent(outcome.player(), ignored -> new HashMap<>())
                .merge(outcome.floor(), outcome.difficulty().next().ordinal(), Math::max);
    }

    private void save(Outcome outcome) {
        validate(outcome);
        var write = new DurableWrite(UUID.randomUUID(), outcome.instance(), NAMESPACE, outcome.id().toString(),
                outcome.id() + (outcome.delivered() ? ":delivered" : outcome.settled() ? ":outcome" : ":started"),
                outcome.delivered() ? 3 : outcome.settled() ? 2 : 1,
                gson.toJson(outcome).getBytes(StandardCharsets.UTF_8));
        var submission = repository.submit(write);
        if (!submission.accepted()) throw new IllegalStateException(submission.detail());
        // ponytail: outcome writes block for fsync; make them asynchronous if this becomes measurable.
        submission.receipt().join();
    }

    private static void validate(Outcome outcome) {
        if (outcome == null || outcome.schemaVersion() != 1 || outcome.id() == null || outcome.instance() == null
                || outcome.player() == null || outcome.floor() == null || !outcome.floor().matches("[a-z0-9][a-z0-9_-]{0,63}")
                || outcome.difficulty() == null || !Double.isFinite(outcome.xp()) || outcome.xp() <= 0) {
            throw new IllegalArgumentException("invalid dungeon outcome");
        }
    }

    public record Outcome(int schemaVersion, UUID id, UUID instance, UUID player, String floor,
                          Difficulty difficulty, boolean successful, double xp, boolean delivered, boolean settled) { }
}
