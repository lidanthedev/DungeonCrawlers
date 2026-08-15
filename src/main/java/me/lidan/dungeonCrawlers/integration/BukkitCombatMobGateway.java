package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.combat.CombatMobGateway;
import me.lidan.dungeonCrawlers.core.identity.EntityIdentity;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Bukkit/Paper boundary for checked MythicMobs entities. */
public final class BukkitCombatMobGateway implements CombatMobGateway {
    private final Server server;
    private final Supplier<World> world;
    private final MythicMobGateway mythic;
    private final BukkitEntityIdentity identity;

    public BukkitCombatMobGateway(Server server, Supplier<World> world,
                                  MythicMobGateway mythic, BukkitEntityIdentity identity) {
        this.server = Objects.requireNonNull(server, "server");
        this.world = Objects.requireNonNull(world, "world");
        this.mythic = Objects.requireNonNull(mythic, "mythic");
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    @Override
    public SpawnResult spawn(UUID instanceId, int roomIndex, String mobId, Point point) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(mobId, "mobId");
        Objects.requireNonNull(point, "point");
        World target = world.get();
        if (target == null) return SpawnResult.failure("generation world is not loaded");
        MythicMobGateway.SpawnResult result = mythic.spawn(mobId,
                new Location(target, point.x() + 0.5, point.y(), point.z() + 0.5), 1.0);
        if (!result.successful() || result.entity() == null || !result.entity().isValid()) {
            return SpawnResult.failure(result.detail());
        }
        Entity entity = result.entity();
        identity.mark(entity, new EntityIdentity(instanceId, roomIndex));
        return new SpawnResult(true, entity.getUniqueId(), result.detail());
    }

    @Override
    public boolean remove(UUID entityId) {
        Entity entity = server.getEntity(Objects.requireNonNull(entityId, "entityId"));
        if (entity == null) return false;
        if (mythic.remove(entity)) return true;
        entity.remove();
        return true;
    }

    @Override
    public boolean isValid(UUID entityId) {
        Entity entity = server.getEntity(Objects.requireNonNull(entityId, "entityId"));
        return entity != null && entity.isValid() && !entity.isDead();
    }
}
