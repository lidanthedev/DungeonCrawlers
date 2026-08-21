package me.lidan.dungeonCrawlers.core.encounter;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Factory and lifecycle contract for a checked boss encounter. */
public interface EncounterFactory {
    Encounter create(EncounterContext context);

    interface Encounter {
        StartResult start();

        TickResult tick(Instant now);

        DeathResult onDeath(UUID entityId);

        void cleanup();

        Optional<UUID> entityId();

        boolean complete();
    }

    record EncounterContext(UUID instanceId, String encounterId, String bossMob, Point bossSpawn,
                            BossEntityGateway entities, Consumer<String> diagnostics) {
        public EncounterContext {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(encounterId, "encounterId");
            Objects.requireNonNull(bossMob, "bossMob");
            Objects.requireNonNull(bossSpawn, "bossSpawn");
            Objects.requireNonNull(entities, "entities");
            Objects.requireNonNull(diagnostics, "diagnostics");
        }
    }

    record StartResult(boolean successful, String detail) {
        public StartResult { Objects.requireNonNull(detail, "detail"); }
        public static StartResult success(String detail) { return new StartResult(true, detail); }
        public static StartResult failure(String detail) { return new StartResult(false, detail); }
    }

    record TickResult(boolean successful, boolean completed, String detail) {
        public TickResult { Objects.requireNonNull(detail, "detail"); }
        public static TickResult running(String detail) { return new TickResult(true, false, detail); }
        public static TickResult complete(String detail) { return new TickResult(true, true, detail); }
        public static TickResult failure(String detail) { return new TickResult(false, false, detail); }
    }

    record DeathResult(boolean accepted, boolean completed, String detail) {
        public DeathResult { Objects.requireNonNull(detail, "detail"); }
        public static DeathResult ignored(String detail) { return new DeathResult(false, false, detail); }
        public static DeathResult accepted(boolean completed, String detail) {
            return new DeathResult(true, completed, detail);
        }
    }
}
