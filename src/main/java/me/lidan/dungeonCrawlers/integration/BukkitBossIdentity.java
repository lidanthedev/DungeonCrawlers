package me.lidan.dungeonCrawlers.integration;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistent identity for the single active boss entity of an instance. */
public final class BukkitBossIdentity {
    private final NamespacedKey instanceKey;

    public BukkitBossIdentity(Plugin plugin) {
        instanceKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "boss_instance_id");
    }

    public void mark(Entity entity, UUID instanceId) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(instanceId, "instanceId");
        entity.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instanceId.toString());
    }

    public Optional<UUID> read(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String value = data.get(instanceKey, PersistentDataType.STRING);
        if (value == null) return Optional.empty();
        try { return Optional.of(UUID.fromString(value)); }
        catch (IllegalArgumentException exception) { return Optional.empty(); }
    }

    public void clear(Entity entity) {
        Objects.requireNonNull(entity, "entity").getPersistentDataContainer().remove(instanceKey);
    }
}
