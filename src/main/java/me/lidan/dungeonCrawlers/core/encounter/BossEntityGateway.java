package me.lidan.dungeonCrawlers.core.encounter;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;

import java.util.Objects;
import java.util.UUID;

/** Main-thread boundary for checked boss entity lifecycle operations. */
public interface BossEntityGateway {
    SpawnResult spawn(UUID instanceId, String mobId, Point point);

    boolean remove(UUID entityId);

    boolean isValid(UUID entityId);

    record SpawnResult(boolean successful, UUID entityId, String detail) {
        public SpawnResult {
            Objects.requireNonNull(detail, "detail");
            if (successful && entityId == null) {
                throw new IllegalArgumentException("successful boss spawn requires an entity id");
            }
        }

        public static SpawnResult success(UUID entityId, String detail) {
            return new SpawnResult(true, Objects.requireNonNull(entityId, "entityId"), detail);
        }

        public static SpawnResult failure(String detail) {
            return new SpawnResult(false, null, detail);
        }
    }
}
