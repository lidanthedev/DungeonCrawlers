package me.lidan.dungeonCrawlers.core.encounter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The built-in one-entity encounter: only the exact spawned entity can complete it. */
public final class BasicBossEncounter implements EncounterFactory.Encounter {
    private final EncounterFactory.EncounterContext context;
    private UUID entityId;
    private boolean started;
    private boolean complete;
    private boolean cleaned;

    public BasicBossEncounter(EncounterFactory.EncounterContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public synchronized EncounterFactory.StartResult start() {
        if (cleaned) return EncounterFactory.StartResult.failure("encounter has been cleaned");
        if (started) return EncounterFactory.StartResult.success("basic boss already started");
        BossEntityGateway.SpawnResult spawned = context.entities().spawn(
                context.instanceId(), context.bossMob(), context.bossSpawn());
        if (!spawned.successful()) return EncounterFactory.StartResult.failure(spawned.detail());
        entityId = spawned.entityId();
        started = true;
        return EncounterFactory.StartResult.success("basic boss spawned entity=" + entityId);
    }

    @Override
    public synchronized EncounterFactory.TickResult tick(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!started) return EncounterFactory.TickResult.failure("basic boss has not started");
        if (complete) return EncounterFactory.TickResult.complete("basic boss defeated");
        if (entityId == null || !context.entities().isValid(entityId)) {
            return EncounterFactory.TickResult.failure("basic boss entity disappeared: " + entityId);
        }
        return EncounterFactory.TickResult.running("basic boss active entity=" + entityId);
    }

    @Override
    public synchronized EncounterFactory.DeathResult onDeath(UUID killedEntityId) {
        Objects.requireNonNull(killedEntityId, "killedEntityId");
        if (!started || complete) return EncounterFactory.DeathResult.ignored("basic boss is not active");
        if (!killedEntityId.equals(entityId)) {
            return EncounterFactory.DeathResult.ignored("entity is not the active boss");
        }
        complete = true;
        return EncounterFactory.DeathResult.accepted(true, "basic boss defeated");
    }

    @Override
    public synchronized void cleanup() {
        if (cleaned) return;
        cleaned = true;
        if (entityId != null && context.entities().isValid(entityId)) context.entities().remove(entityId);
    }

    @Override
    public synchronized Optional<UUID> entityId() {
        return Optional.ofNullable(entityId);
    }

    @Override
    public synchronized boolean complete() { return complete; }
}
