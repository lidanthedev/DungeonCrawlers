package me.lidan.dungeonCrawlers.integration.citizens;

import me.lidan.dungeonCrawlers.integration.ClassSelectorNpcService;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.Trait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Citizens-backed, in-memory class selector NPCs. */
public final class CitizensClassSelectorNpcService implements ClassSelectorNpcService, Listener {
    private static final String REGISTRY_NAME = "DungeonCrawlers-ClassSelectors";
    private static final String NPC_NAME = "Class Selector";

    private final Plugin plugin;
    private final BiConsumer<Player, UUID> menuOpener;
    private final String skinName;
    private final NPCRegistry registry;
    private final Map<UUID, NPC> npcs = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> instanceByNpcId = new ConcurrentHashMap<>();

    public CitizensClassSelectorNpcService(Plugin plugin, BiConsumer<Player, UUID> menuOpener) {
        this(plugin, menuOpener, "");
    }

    public CitizensClassSelectorNpcService(Plugin plugin, BiConsumer<Player, UUID> menuOpener, String skinName) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menuOpener = Objects.requireNonNull(menuOpener, "menuOpener");
        this.skinName = skinName == null ? "" : skinName.trim();
        if (!CitizensAPI.hasImplementation()) {
            throw new IllegalStateException("Citizens API is not available");
        }
        this.registry = CitizensAPI.createInMemoryNPCRegistry(REGISTRY_NAME);
    }

    @Override
    public boolean available() {
        try {
            return CitizensAPI.hasImplementation()
                    && plugin.getServer() != null
                    && plugin.getServer().getPluginManager().isPluginEnabled("Citizens");
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    @Override
    public synchronized void createFor(UUID instanceId, Location location) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(location, "location");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Class selector NPCs must be created on the server thread");
        }
        removeFor(instanceId);
        NPC npc = registry.createNPC(EntityType.PLAYER, NPC_NAME);
        try {
            npc.setProtected(true);
            npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
            applyConfiguredSkin(npc);
            if (!npc.spawn(location, SpawnReason.PLUGIN)) {
                throw new IllegalStateException("Citizens could not spawn the class selector NPC");
            }
            npcs.put(instanceId, npc);
            instanceByNpcId.put(npc.getId(), instanceId);
        } catch (RuntimeException exception) {
            cleanupNpc(npc);
            throw exception;
        } catch (LinkageError failure) {
            cleanupNpc(npc);
            throw new IllegalStateException("Citizens became unavailable while spawning the class selector NPC",
                    failure);
        }
    }

    private void applyConfiguredSkin(NPC npc) {
        if (skinName.isBlank()) return;
        try {
            Class<? extends Trait> traitClass = CitizensAPI.getTraitFactory().getTraitClass("skintrait");
            if (traitClass == null) {
                warnSkinFailure("Citizens' skin trait is unavailable");
                return;
            }
            Trait trait = npc.getOrAddTrait(traitClass);
            Method setter = traitClass.getMethod("setSkinName", String.class);
            setter.invoke(trait, skinName);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnSkinFailure("configured skin could not be applied");
        }
    }

    private void warnSkinFailure(String detail) {
        plugin.getLogger().warning("DungeonCrawlers: " + detail + "; using Citizens' default skin");
    }

    @Override
    public synchronized void removeFor(UUID instanceId) {
        if (instanceId == null) return;
        NPC npc = npcs.remove(instanceId);
        if (npc == null) return;
        instanceByNpcId.remove(npc.getId(), instanceId);
        cleanupNpc(npc);
    }

    @Override
    public synchronized void shutdown() {
        for (UUID instanceId : java.util.List.copyOf(npcs.keySet())) {
            removeFor(instanceId);
        }
        try {
            registry.deregisterAll();
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().fine("Class selector NPC registry cleanup failed: " + failure.getMessage());
        } finally {
            try {
                if (CitizensAPI.hasImplementation()) CitizensAPI.removeNamedNPCRegistry(REGISTRY_NAME);
            } catch (RuntimeException | LinkageError failure) {
                plugin.getLogger().fine("Class selector NPC registry release failed: " + failure.getMessage());
            }
            npcs.clear();
            instanceByNpcId.clear();
        }
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        try {
            NPC npc = event.getNPC();
            UUID instanceId = instanceByNpcId.get(npc.getId());
            if (instanceId == null
                    || npcs.get(instanceId) != npc
                    || npc.getOwningRegistry() != registry
                    || !npc.isSpawned()
                    || npc.getEntity() == null
                    || !registry.isNPC(npc.getEntity())) {
                return;
            }
            menuOpener.accept(event.getClicker(), instanceId);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().fine("Class selector NPC interaction ignored: " + failure.getMessage());
        }
    }

    private void cleanupNpc(NPC npc) {
        boolean despawned = false;
        try {
            despawned = npc.despawn(DespawnReason.PLUGIN);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().fine("Class selector NPC despawn failed: " + failure.getMessage());
        }
        if (!despawned) {
            try {
                var entity = npc.getEntity();
                if (entity != null && entity.isValid()) entity.remove();
            } catch (RuntimeException | LinkageError fallbackFailure) {
                plugin.getLogger().fine("Class selector NPC entity removal failed: "
                        + fallbackFailure.getMessage());
            }
        }
        try {
            registry.deregister(npc);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().fine("Class selector NPC deregistration failed: " + failure.getMessage());
        }
    }
}
