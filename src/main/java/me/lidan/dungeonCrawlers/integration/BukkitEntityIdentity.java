package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.identity.EntityIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistent instance/room identity used by mob reconciliation and protection. */
public final class BukkitEntityIdentity {
    private final NamespacedKey instanceKey;
    private final NamespacedKey roomKey;

    public BukkitEntityIdentity(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        instanceKey = new NamespacedKey(plugin, "instance_id");
        roomKey = new NamespacedKey(plugin, "room_index");
    }

    public void mark(Entity entity, EntityIdentity identity) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(identity, "identity");
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(instanceKey, PersistentDataType.STRING, identity.instanceId().toString());
        data.set(roomKey, PersistentDataType.INTEGER, identity.roomIndex());
    }

    public Optional<EntityIdentity> read(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String instance = data.get(instanceKey, PersistentDataType.STRING);
        Integer room = data.get(roomKey, PersistentDataType.INTEGER);
        if (instance == null || room == null) return Optional.empty();
        try { return Optional.of(new EntityIdentity(UUID.fromString(instance), room)); }
        catch (IllegalArgumentException exception) { return Optional.empty(); }
    }

    public boolean belongsTo(Entity entity, UUID instanceId) {
        return read(entity).map(identity -> identity.instanceId().equals(instanceId)).orElse(false);
    }

    public void clear(Entity entity) {
        Objects.requireNonNull(entity, "entity").getPersistentDataContainer().remove(instanceKey);
        entity.getPersistentDataContainer().remove(roomKey);
    }
}
