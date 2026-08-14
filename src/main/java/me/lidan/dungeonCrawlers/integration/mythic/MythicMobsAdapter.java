package me.lidan.dungeonCrawlers.integration.mythic;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import me.lidan.dungeonCrawlers.integration.MythicMobGateway;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class MythicMobsAdapter implements MythicMobGateway {
    @Override
    public boolean isConfigured(String mobId) {
        return MythicBukkit.inst().getMobManager().getMythicMob(mobId).isPresent();
    }

    @Override
    public SpawnResult spawn(String mobId, Location location, double level) {
        if (!isConfigured(mobId)) {
            return new SpawnResult(false, null, "Unknown MythicMob: " + mobId);
        }
        try {
            ActiveMob activeMob = MythicBukkit.inst().getMobManager().spawnMob(mobId, location, level);
            Entity entity = activeMob == null ? null : activeMob.getEntity().getBukkitEntity();
            return new SpawnResult(entity != null && entity.isValid(), entity,
                    entity == null ? "MythicMobs returned no Bukkit entity" : "spawned=" + entity.getUniqueId());
        } catch (RuntimeException exception) {
            return new SpawnResult(false, null, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    @Override
    public boolean isMythicMob(Entity entity) {
        return MythicBukkit.inst().getMobManager().isMythicMob(entity);
    }

    @Override
    public boolean remove(Entity entity) {
        return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId())
                .map(activeMob -> {
                    activeMob.remove();
                    return true;
                })
                .orElse(false);
    }
}

