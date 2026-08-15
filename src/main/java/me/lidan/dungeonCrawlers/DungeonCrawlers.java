package me.lidan.dungeonCrawlers;

import dev.triumphteam.gui.guis.BaseGui;
import me.lidan.dungeonCrawlers.commands.DungeonCrawlersCommand;
import me.lidan.dungeonCrawlers.commands.DungeonAuthoringCommand;
import me.lidan.dungeonCrawlers.commands.DungeonGenerationCommand;
import me.lidan.dungeonCrawlers.commands.InstanceIdSuggestionProvider;
import me.lidan.dungeonCrawlers.authoring.TemplateAuthoringService;
import me.lidan.dungeonCrawlers.authoring.TemplateCatalogLoader;
import me.lidan.dungeonCrawlers.compatibility.CompatibilityService;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.config.registry.EncounterRegistry;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.core.reservation.PlayerReservationService;
import me.lidan.dungeonCrawlers.core.chunk.ChunkTicketBudget;
import me.lidan.dungeonCrawlers.core.door.DoorService;
import me.lidan.dungeonCrawlers.core.generation.GenerationPreparationProvider;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.generation.SlotAllocator;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.protection.WorldProtectionService;
import me.lidan.dungeonCrawlers.core.snapshot.PlayerSnapshotService;
import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.EmeraldPolicy;
import me.lidan.dungeonCrawlers.core.template.TemplateValidator;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseFourCommand;
import me.lidan.dungeonCrawlers.integration.BukkitChunkTicketService;
import me.lidan.dungeonCrawlers.integration.BukkitEntityIdentity;
import me.lidan.dungeonCrawlers.integration.BukkitWorldProtectionListener;
import me.lidan.dungeonCrawlers.integration.parties.PartyProviders;
import me.lidan.dungeonCrawlers.integration.worldedit.FaweGenerationAdapter;
import me.lidan.dungeonCrawlers.integration.worldedit.WorldEditAdapter;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.FileDurableRepository;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.Lamp;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DungeonCrawlers extends JavaPlugin {
    private Lamp.Builder<BukkitCommandActor> commandHandlerBuilder;
    private ConfigRegistryService configRegistry;
    private PlayerReservationService reservations;
    private DurableRepository durableRepository;
    private BoostedCustomConfig mainConfig;
    private BoostedConfigFactory configFactory;
    private TemplateAuthoringService authoring;
    private GenerationService generation;
    private ExecutorService generationExecutor;
    private java.time.Clock phaseClock;
    private String generationWorldName;
    private CentralUpdateService centralUpdates;
    private DoorService doors;
    private PlayerSnapshotService playerSnapshots;
    private WorldProtectionService protectionPolicy;
    private TeleportPermitService teleportPermits;
    private BukkitEntityIdentity entityIdentity;
    private BukkitChunkTicketService chunkTickets;

    @Override
    public void onEnable() {
        // Plugin startup logic
        commandHandlerBuilder = BukkitLamp.builder(this);
        registerSerializer();

        saveDefaultResources();
        initializePhaseOneServices();
        registerCommandResolvers();
        registerCommandCompletions();
        registerCommands();
        registerEvents();

        startTasks();
    }

    private void initializePhaseOneServices() {
        try {
            configFactory = new BoostedConfigFactory();
            mainConfig = configFactory.openMainConfig(
                    new File(getDataFolder(), "config.yml").toPath(),
                    getDataFolder().toPath().resolve("backups/config-migrations"));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load config.yml", exception);
        }
        if (!mainConfig.contains(BoostedConfigFactory.VERSION_ROUTE, true)
                || BoostedConfigFactory.schemaVersion(mainConfig) != BoostedConfigFactory.CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("config.yml schema-version must be "
                    + BoostedConfigFactory.CURRENT_SCHEMA_VERSION);
        }
        EncounterRegistry encounters = new EncounterRegistry();
        int backupRetention = configuredBackupRetention();
        configRegistry = new ConfigRegistryService(getDataFolder().toPath(), encounters, backupRetention);
        ConfigRegistryService.ReloadResult loaded = configRegistry.initialize();
        if (!loaded.swapped()) {
            throw new IllegalStateException("Invalid DungeonCrawlers configuration: " + loaded.errors());
        }
        loaded.warnings().forEach(getLogger()::warning);
        reservations = new PlayerReservationService();
        authoring = new TemplateAuthoringService(getDataFolder().toPath(), configFactory,
                configRegistry::snapshot, backupRetention);
        int queueCapacity = loaded.snapshot().floors().values().stream()
                .mapToInt(floor -> floor.limits().repositoryQueueCapacity()).max().orElse(1_000);
        durableRepository = new FileDurableRepository(getDataFolder().toPath().resolve("runtime"), queueCapacity,
                callback -> getServer().getScheduler().runTask(this, callback));
        initializePhaseThreeServices();
        getLogger().info("Loaded Phase 3 config hash " + loaded.snapshot().hash());
    }

    private void initializePhaseThreeServices() {
        String worldName = mainConfig.getString("generation.world", "dungeon_instances").trim();
        int capacity = configuredInteger("generation.capacity", 4, 1, 256);
        int spacing = configuredInteger("generation.slot-spacing", 10_000, 10_000, 10_000);
        int margin = configuredInteger("generation.slot-margin", 500, 1, 4_999);
        int baseY = configuredInteger("generation.base-y", 64, -2_048, 2_048);
        generationExecutor = Executors.newFixedThreadPool(Math.max(2,
                Math.min(8, Runtime.getRuntime().availableProcessors())), runnable -> {
            Thread thread = new Thread(runnable, "dungeoncrawlers-generation");
            thread.setDaemon(true);
            return thread;
        });
        FaweGenerationAdapter generationWorld = new FaweGenerationAdapter(this, generationExecutor);
        var worldCheck = generationWorld.ensureDedicatedVoidWorld(worldName);
        if (!worldCheck.successful()) throw new IllegalStateException(worldCheck.detail());
        generationWorldName = worldName;
        SlotAllocator slots = new SlotAllocator(new SlotAllocator.Settings(capacity, spacing, margin, baseY,
                worldCheck.minimumY(), worldCheck.maximumY()));
        EmeraldPolicy emeraldPolicy;
        try {
            emeraldPolicy = EmeraldPolicy.valueOf(mainConfig
                    .getString("authoring.emerald-marker-policy", "replace").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("config.yml authoring.emerald-marker-policy must be replace or retain");
        }
        TemplateCatalogLoader catalog = new TemplateCatalogLoader(authoring, new WorldEditAdapter(),
                new TemplateValidator(), emeraldPolicy);
        generation = new GenerationService(reservations, slots, durableRepository, generationWorld,
                new GenerationPreparationProvider(catalog, authoring, new LayoutPlanner()), generationExecutor,
                callback -> getServer().getScheduler().runTask(this, callback), getServer()::isPrimaryThread,
                getLogger()::info, phaseClock(), worldName);
        generation.recover();
        initializePhaseFourServices(worldName);
    }

    private java.time.Clock phaseClock() {
        if (phaseClock == null) phaseClock = Clock.systemUTC();
        return phaseClock;
    }

    private void initializePhaseFourServices(String worldName) {
        centralUpdates = new CentralUpdateService(phaseClock(), getLogger()::info);
        doors = new DoorService();
        playerSnapshots = new PlayerSnapshotService(durableRepository);
        protectionPolicy = new WorldProtectionService();
        teleportPermits = new TeleportPermitService();
        entityIdentity = new BukkitEntityIdentity(this);
        int maximumPerInstance = configRegistry.snapshot().floors().values().stream()
                .mapToInt(floor -> floor.limits().maxLoadedChunksPerInstance()).max().orElse(256);
        int maximumTotal = Math.multiplyExact(maximumPerInstance, generation.slots().size());
        org.bukkit.World world = getServer().getWorld(worldName);
        if (world == null) throw new IllegalStateException("generation world disappeared during Phase 4 setup");
        chunkTickets = new BukkitChunkTicketService(this, world,
                new ChunkTicketBudget(maximumPerInstance, maximumTotal));
    }

    private int configuredBackupRetention() {
        String route = "backups.retention-count";
        if (!mainConfig.contains(route, true)) return ConfigRegistryService.DEFAULT_BACKUP_RETENTION;
        Object value = mainConfig.get(route);
        try {
            int retention = value instanceof Number number
                    ? new BigDecimal(number.toString()).intValueExact() : -1;
            if (retention < 1 || retention > 1_000) throw new ArithmeticException();
            return retention;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalStateException("config.yml backups.retention-count must be an integer in 1..1000");
        }
    }

    private int configuredInteger(String route, int defaultValue, int minimum, int maximum) {
        if (!mainConfig.contains(route, true)) return defaultValue;
        Object value = mainConfig.get(route);
        try {
            int parsed = value instanceof Number number ? new BigDecimal(number.toString()).intValueExact() : -1;
            if (parsed < minimum || parsed > maximum) throw new ArithmeticException();
            return parsed;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalStateException("config.yml " + route + " must be an integer in "
                    + minimum + ".." + maximum);
        }
    }

    private void registerSerializer() {
        // Register custom serializers if needed
    }

    private void saveDefaultResources() {
        saveResource("classes.yml", false);
        saveResource("blessings.yml", false);
        saveResource("rooms.yml", false);
        saveResource("floors/floor_1.yml", false);
        saveResource("config.yml", false);
    }

    private void registerCommandResolvers() {
        // Register custom command argument resolvers if needed
    }

    private void registerCommandCompletions() {
        // Register custom command completions if needed
    }

    private void registerCommands() {
        // Register commands
        commandHandlerBuilder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
            if (annotation.value() != InstanceIdSuggestionProvider.class) return null;
            return new InstanceIdSuggestionProvider<BukkitCommandActor>(() -> generation.instances().stream()
                    .map(GenerationService.InstanceSnapshot::instanceId)
                    .map(java.util.UUID::toString)
                    .toList());
        });
        Lamp<BukkitCommandActor> commandHandler = commandHandlerBuilder.build();
        commandHandler.register(new DungeonCrawlersCommand(this,
                new CompatibilityService(this, mainConfig, configRegistry), mainConfig, configRegistry,
                reservations, durableRepository));
        commandHandler.register(new DungeonAuthoringCommand(mainConfig, configRegistry, reservations, authoring,
                generation::activeTemplateIds));
        commandHandler.register(new DungeonGenerationCommand(configRegistry,
                PartyProviders.forServer(getServer()), generation, getServer(),
                generationWorldName,
                teleportPermits, phaseClock()));
        commandHandler.register(new DungeonPhaseFourCommand(centralUpdates, doors, protectionPolicy,
                teleportPermits, playerSnapshots, getServer(), this, phaseClock(),
                generationWorldName,
                () -> generation.protectionRegions().stream().map(WorldProtectionService.InstanceRegion::from).toList()));
    }

    private void registerEvents() {
        registerEvent(new BukkitWorldProtectionListener(protectionPolicy,
                () -> generation.protectionRegions().stream().map(WorldProtectionService.InstanceRegion::from).toList(),
                teleportPermits, phaseClock()));
    }

    private void startTasks() {
        getServer().getScheduler().runTaskTimer(this, (Runnable) centralUpdates::tick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (generation != null) generation.freezeForDisable();
        if (centralUpdates != null) centralUpdates.clear();
        getServer().getScheduler().cancelTasks(this);
        if (durableRepository != null) durableRepository.close();
        if (generationExecutor != null) generationExecutor.shutdownNow();
        closeAllGuis();
    }

    /**
     * Close all guis
     */
    private void closeAllGuis() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BaseGui) {
                player.closeInventory();
            }
        });
    }

    /**
     * Register event
     *
     * @param listener the listener to register
     */
    private void registerEvent(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    /**
     * Save a resource to a file path
     * Used to save resources to subdirectories in the plugin folder
     *
     * @param resource the resource
     * @param path     the path as File object
     */
    private void saveResource(String resource, File path) {
        if (!path.exists()) {
            path.getParentFile().mkdirs();
            try (InputStream in = getResource(resource);
                 FileOutputStream out = new FileOutputStream(path)) {
                if (in == null) {
                    getLogger().warning("Resource not found: " + resource);
                    return;
                }
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static DungeonCrawlers getInstance() {
        return JavaPlugin.getPlugin(DungeonCrawlers.class);
    }
}
