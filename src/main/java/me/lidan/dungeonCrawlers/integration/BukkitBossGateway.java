package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.encounter.BossEntityGateway;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Bukkit boundary for the checked MythicMobs boss entity. */
public final class BukkitBossGateway implements BossEntityGateway {
    private final Server server;
    private final Supplier<World> world;
    private final MythicMobGateway mythic;
    private final BukkitBossIdentity identity;

    public BukkitBossGateway(Server server, Supplier<World> world, MythicMobGateway mythic,
                             BukkitBossIdentity identity) {
        this.server = Objects.requireNonNull(server, "server");
        this.world = Objects.requireNonNull(world, "world");
        this.mythic = Objects.requireNonNull(mythic, "mythic");
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    @Override
    public SpawnResult spawn(UUID instanceId, String mobId, Point point) {
        World target = world.get();
        if (target == null) return SpawnResult.failure("generation world is not loaded");
        MythicMobGateway.SpawnResult result = mythic.spawn(mobId,
                new Location(target, point.x() + 0.5, point.y(), point.z() + 0.5), 1.0);
        if (!result.successful() || result.entity() == null || !result.entity().isValid()) {
            return SpawnResult.failure(result.detail());
        }
        identity.mark(result.entity(), instanceId);
        return SpawnResult.success(result.entity().getUniqueId(), result.detail());
    }

    @Override
    public boolean remove(UUID entityId) {
        Entity entity = server.getEntity(Objects.requireNonNull(entityId, "entityId"));
        if (entity == null) return false;
        boolean removed = mythic.remove(entity);
        if (!removed) entity.remove();
        identity.clear(entity);
        return removed || !entity.isValid();
    }

    @Override
    public boolean isValid(UUID entityId) {
        Entity entity = server.getEntity(Objects.requireNonNull(entityId, "entityId"));
        return entity != null && entity.isValid() && !entity.isDead();
    }
}
