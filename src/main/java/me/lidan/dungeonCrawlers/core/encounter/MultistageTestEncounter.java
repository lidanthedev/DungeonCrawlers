package me.lidan.dungeonCrawlers.core.encounter;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Small built-in encounter used by the Phase 9 staging gate to exercise multiple stages. */
public final class MultistageTestEncounter implements EncounterFactory.Encounter {
    private final EncounterFactory.EncounterContext context;
    private UUID entityId;
    private int stage;
    private boolean complete;
    private boolean cleaned;

    public MultistageTestEncounter(EncounterFactory.EncounterContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public synchronized EncounterFactory.StartResult start() {
        if (cleaned) return EncounterFactory.StartResult.failure("encounter has been cleaned");
        if (stage != 0) return EncounterFactory.StartResult.success("multistage test already started");
        return spawnNextStage("stage 1 spawned");
    }

    @Override
    public synchronized EncounterFactory.TickResult tick(Instant now) {
        Objects.requireNonNull(now, "now");
        if (complete) return EncounterFactory.TickResult.complete("multistage test complete");
        if (stage == 0) return EncounterFactory.TickResult.failure("multistage test has not started");
        if (entityId == null || !context.entities().isValid(entityId)) {
            return EncounterFactory.TickResult.failure("multistage test entity disappeared at stage " + stage);
        }
        return EncounterFactory.TickResult.running("multistage test stage=" + stage);
    }

    @Override
    public synchronized EncounterFactory.DeathResult onDeath(UUID killedEntityId) {
        Objects.requireNonNull(killedEntityId, "killedEntityId");
        if (complete || stage == 0) return EncounterFactory.DeathResult.ignored("multistage test is not active");
        if (!killedEntityId.equals(entityId)) return EncounterFactory.DeathResult.ignored("entity is not active stage");
        if (stage == 1) {
            EncounterFactory.StartResult next = spawnNextStage("stage 2 spawned");
            return next.successful() ? EncounterFactory.DeathResult.accepted(false, next.detail())
                    : EncounterFactory.DeathResult.ignored(next.detail());
        }
        complete = true;
        return EncounterFactory.DeathResult.accepted(true, "multistage test defeated");
    }

    @Override
    public synchronized void cleanup() {
        if (cleaned) return;
        cleaned = true;
        if (entityId != null && context.entities().isValid(entityId)) context.entities().remove(entityId);
    }

    @Override
    public synchronized Optional<UUID> entityId() { return Optional.ofNullable(entityId); }

    @Override
    public synchronized boolean complete() { return complete; }

    private EncounterFactory.StartResult spawnNextStage(String successDetail) {
        BossEntityGateway.SpawnResult spawned = context.entities().spawn(
                context.instanceId(), context.bossMob(), context.bossSpawn());
        if (!spawned.successful()) return EncounterFactory.StartResult.failure(spawned.detail());
        entityId = spawned.entityId();
        stage++;
        return EncounterFactory.StartResult.success(successDetail);
    }
}
