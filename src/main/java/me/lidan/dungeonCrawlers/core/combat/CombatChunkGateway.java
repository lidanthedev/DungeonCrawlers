package me.lidan.dungeonCrawlers.core.combat;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;

import java.util.UUID;

/** Bounded active-room chunk ticket boundary. */
public interface CombatChunkGateway {
    boolean acquire(UUID instanceId, Bounds bounds);

    int release(UUID instanceId, Bounds bounds);

    int releaseAll(UUID instanceId);
}
