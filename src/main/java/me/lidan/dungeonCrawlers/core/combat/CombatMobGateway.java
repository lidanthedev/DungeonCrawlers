package me.lidan.dungeonCrawlers.core.combat;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;

import java.util.Objects;
import java.util.UUID;

/** Main-thread boundary for checked MythicMobs spawning and entity lifecycle operations. */
public interface CombatMobGateway {
    SpawnResult spawn(UUID instanceId, int roomIndex, String mobId, Point point);

    boolean remove(UUID entityId);

    boolean isValid(UUID entityId);

    record SpawnResult(boolean successful, UUID entityId, String detail) {
        public SpawnResult {
            Objects.requireNonNull(detail, "detail");
            if (successful && entityId == null) {
                throw new IllegalArgumentException("successful spawn must include an entity id");
            }
        }

        public static SpawnResult failure(String detail) {
            return new SpawnResult(false, null, detail);
        }
    }
}
