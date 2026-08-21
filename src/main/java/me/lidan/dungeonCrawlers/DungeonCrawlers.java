package me.lidan.dungeonCrawlers;

import dev.triumphteam.gui.guis.BaseGui;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.cavecrawlers.stats.StatsManager;
import me.lidan.dungeonCrawlers.commands.DungeonCrawlersCommand;
import me.lidan.dungeonCrawlers.commands.DungeonAuthoringCommand;
import me.lidan.dungeonCrawlers.commands.DungeonGenerationCommand;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseFiveCommand;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseSixCommand;
import me.lidan.dungeonCrawlers.commands.ClassIdSuggestionProvider;
import me.lidan.dungeonCrawlers.commands.FloorIdSuggestionProvider;
import me.lidan.dungeonCrawlers.commands.InstanceIdSuggestionProvider;
import me.lidan.dungeonCrawlers.commands.OfflinePlayerSuggestionProvider;
import me.lidan.dungeonCrawlers.commands.RoomIdSuggestionProvider;
import me.lidan.dungeonCrawlers.authoring.TemplateAuthoringService;
import me.lidan.dungeonCrawlers.authoring.TemplateCatalogLoader;
import me.lidan.dungeonCrawlers.compatibility.CompatibilityService;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.config.registry.EncounterRegistry;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.core.reservation.PlayerReservationService;
import me.lidan.dungeonCrawlers.core.chunk.ChunkTicketBudget;
import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.door.DoorService;
import me.lidan.dungeonCrawlers.core.generation.GenerationPreparationProvider;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.generation.SlotAllocator;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.protection.WorldProtectionService;
import me.lidan.dungeonCrawlers.core.snapshot.PlayerSnapshotService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import me.lidan.dungeonCrawlers.core.portal.PortalEncounterService;
import me.lidan.dungeonCrawlers.core.encounter.EncounterFactoryRegistry;
import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.state.StateTransitionService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.EmeraldPolicy;
import me.lidan.dungeonCrawlers.core.template.TemplateValidator;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseFourCommand;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseSevenCommand;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseEightCommand;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseNineCommand;
import me.lidan.dungeonCrawlers.commands.BlessingIdSuggestionProvider;
import me.lidan.dungeonCrawlers.integration.BukkitChunkTicketService;
import me.lidan.dungeonCrawlers.integration.BukkitCombatListener;
import me.lidan.dungeonCrawlers.integration.BukkitCombatMobGateway;
import me.lidan.dungeonCrawlers.integration.BukkitEntityIdentity;
import me.lidan.dungeonCrawlers.integration.BukkitProgressBarService;
import me.lidan.dungeonCrawlers.integration.BukkitBossGateway;
import me.lidan.dungeonCrawlers.integration.BukkitBossIdentity;
import me.lidan.dungeonCrawlers.integration.BukkitPortalBossListener;
import me.lidan.dungeonCrawlers.integration.BukkitPortalParticipantGateway;
import me.lidan.dungeonCrawlers.integration.ThrottledDungeonActionBar;
import me.lidan.dungeonCrawlers.integration.BukkitWorldProtectionListener;
import me.lidan.dungeonCrawlers.integration.BukkitDungeonRunListener;
import me.lidan.dungeonCrawlers.integration.BukkitDungeonLifecycleListener;
import me.lidan.dungeonCrawlers.integration.BukkitDungeonActionBar;
import me.lidan.dungeonCrawlers.integration.BukkitGhostState;
import me.lidan.dungeonCrawlers.integration.mythic.MythicMobsAdapter;
import me.lidan.dungeonCrawlers.integration.cave.CaveActionBarAdapter;
import me.lidan.dungeonCrawlers.integration.parties.PartyProviders;
import me.lidan.dungeonCrawlers.integration.worldedit.FaweGenerationAdapter;
import me.lidan.dungeonCrawlers.integration.worldedit.WorldEditAdapter;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.FileDurableRepository;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.title.Title;
import revxrsal.commands.Lamp;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
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
    private CombatRoomService combat;
    private RunPreparationService runPreparation;
    private DungeonPhaseFiveCommand phaseFiveCommand;
    private BukkitProgressBarService progressBars;
    private SecretDiscoveryService phaseSeven;
    private PortalEncounterService phaseNine;
    private BukkitBossIdentity bossIdentity;
    private MythicMobsAdapter mythicMobs;
    private PlayerLifecycleService lifecycle;
    private volatile boolean disabling;

    @Override
    public void onEnable() {
        // Plugin startup logic
        disabling = false;
        commandHandlerBuilder = BukkitLamp.builder(this);
        progressBars = new BukkitProgressBarService(this);
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
        try {
            migrateVersionedDataConfigs();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot migrate registry configuration", exception);
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
                getLogger()::info, phaseClock(), worldName, progress -> {
                    if (progress.terminal()) {
                        if (progress.successful()) progressBars.complete(progress.instanceId(), progress.detail());
                        else progressBars.fail(progress.instanceId(), progress.detail());
                    } else {
                        progressBars.update(progress.instanceId(), progress.progress(), progress.detail());
                    }
                });
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
        mythicMobs = new MythicMobsAdapter();
        chunkTickets = new BukkitChunkTicketService(this, world,
                new ChunkTicketBudget(maximumPerInstance, maximumTotal));
        combat = new CombatRoomService(
                new BukkitCombatMobGateway(getServer(), this::generationWorld,
                        mythicMobs, entityIdentity),
                chunkTickets, getLogger()::info, this::notifyCombatRoom);
        runPreparation = new RunPreparationService(doors, centralUpdates, new StateTransitionService(), phaseClock(),
                instanceId -> {
                    var result = combat.activateFirst(instanceId);
                    if (!result.successful()) {
                        throw new IllegalStateException(result.detail());
                    }
                    getLogger().info("instance=" + instanceId + " first room activated");
                }, getLogger()::warning, instanceId -> {
                    if (lifecycle != null) lifecycle.cleanup(instanceId);
                    if (phaseSeven != null) phaseSeven.cleanup(instanceId);
                    if (phaseNine != null) phaseNine.cleanup(instanceId);
                    combat.cleanup(instanceId);
                    generation.cancel(instanceId);
                }, true);
        phaseSeven = new SecretDiscoveryService(configRegistry::snapshot);
        lifecycle = new PlayerLifecycleService(centralUpdates, phaseClock(), this::handleLifecycleNotice);
        bossIdentity = new BukkitBossIdentity(this);
        phaseNine = new PortalEncounterService(centralUpdates, runPreparation,
                EncounterFactoryRegistry.withBasic(),
                new BukkitBossGateway(getServer(), this::generationWorld, mythicMobs, bossIdentity),
                new BukkitPortalParticipantGateway(getServer(), this::generationWorld, generationWorldName,
                        runPreparation, lifecycle, teleportPermits, phaseClock()),
                phaseClock(), getLogger()::warning);
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
        saveDefaultResourceIfMissing("classes.yml");
        saveDefaultResourceIfMissing("blessings.yml");
        saveDefaultResourceIfMissing("rooms.yml");
        saveDefaultResourceIfMissing("floors/floor_1.yml");
        saveDefaultResourceIfMissing("config.yml");
    }

    private void saveDefaultResourceIfMissing(String resource) {
        if (new File(getDataFolder(), resource).exists()) return;
        saveResource(resource, false);
    }

    private void migrateVersionedDataConfigs() throws IOException {
        Path blessingsPath = getDataFolder().toPath().resolve("blessings.yml");
        try {
            configFactory.openVersionedConfig(blessingsPath, "blessings.yml", 2);
        } finally {
            configFactory.release(blessingsPath);
        }
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
        commandHandlerBuilder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
            if (annotation.value() != ClassIdSuggestionProvider.class) return null;
            return new ClassIdSuggestionProvider<BukkitCommandActor>(() -> configRegistry.snapshot().classes().keySet());
        });
        commandHandlerBuilder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
            if (annotation.value() != FloorIdSuggestionProvider.class) return null;
            return new FloorIdSuggestionProvider<BukkitCommandActor>(() -> configRegistry.snapshot().floors().keySet());
        });
        commandHandlerBuilder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
            if (annotation.value() != RoomIdSuggestionProvider.class) return null;
            return new RoomIdSuggestionProvider<BukkitCommandActor>(() -> configRegistry.snapshot().rooms().keySet());
        });
        commandHandlerBuilder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
            if (annotation.value() != BlessingIdSuggestionProvider.class) return null;
            return new BlessingIdSuggestionProvider<BukkitCommandActor>(
                    () -> configRegistry.snapshot().blessings().keySet());
        });
        commandHandlerBuilder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
            if (annotation.value() != OfflinePlayerSuggestionProvider.class) return null;
            return new OfflinePlayerSuggestionProvider<BukkitCommandActor>(() -> Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).filter(java.util.Objects::nonNull).toList());
        });
        phaseFiveCommand = new DungeonPhaseFiveCommand(configRegistry, PartyProviders.forServer(getServer()),
                generation, runPreparation, playerSnapshots, teleportPermits, getServer(), this, phaseClock(),
                generationWorldName,
                new ThrottledDungeonActionBar(new BukkitDungeonActionBar(new CaveActionBarAdapter()), phaseClock()),
                new DungeonPhaseFiveCommand.PhaseServices(combat, progressBars, phaseSeven, lifecycle, phaseNine));
        Lamp<BukkitCommandActor> commandHandler = commandHandlerBuilder.build();
        commandHandler.register(new DungeonCrawlersCommand(this,
                new CompatibilityService(this, mainConfig, configRegistry), mainConfig, configRegistry,
                reservations, durableRepository, generation, phaseFiveCommand::cancelFromAdmin));
        commandHandler.register(new DungeonAuthoringCommand(this, mainConfig, configRegistry, reservations, authoring,
                generation::activeTemplateIds, progressBars));
        commandHandler.register(new DungeonGenerationCommand(configRegistry,
                PartyProviders.forServer(getServer()), generation, getServer(),
                generationWorldName,
                teleportPermits, phaseClock(), phaseFiveCommand::cancelFromAdmin, runPreparation));
        commandHandler.register(phaseFiveCommand);
        commandHandler.register(new DungeonPhaseSixCommand(combat, runPreparation));
        commandHandler.register(new DungeonPhaseSevenCommand(phaseSeven, runPreparation));
        commandHandler.register(new DungeonPhaseEightCommand(lifecycle, runPreparation, phaseFiveCommand));
        commandHandler.register(new DungeonPhaseNineCommand(phaseNine, runPreparation));
        commandHandler.register(new DungeonPhaseFourCommand(centralUpdates, doors, protectionPolicy,
                teleportPermits, playerSnapshots, getServer(), this, phaseClock(),
                generationWorldName,
                () -> generation.protectionRegions().stream().map(WorldProtectionService.InstanceRegion::from).toList(),
                runPreparation));
    }

    private void registerEvents() {
        registerEvent(new BukkitWorldProtectionListener(protectionPolicy,
                () -> generation.protectionRegions().stream().map(WorldProtectionService.InstanceRegion::from).toList(),
                teleportPermits, phaseClock()));
        registerEvent(new BukkitDungeonRunListener(phaseFiveCommand, runPreparation, generationWorldName, phaseSeven));
        registerEvent(new BukkitDungeonLifecycleListener(lifecycle, runPreparation, this, phaseClock(),
                phaseFiveCommand::recoverOnJoin, phaseFiveCommand::leaveFromSpawn));
        registerEvent(new BukkitCombatListener(combat, entityIdentity, generationWorldName, () -> disabling,
                bossIdentity, phaseNine));
        registerEvent(new BukkitPortalBossListener(this, phaseNine, runPreparation, generationWorldName));
        // PlugMan-style reloads do not emit PlayerJoinEvent; repair any durable snapshots for players
        // who stayed online while the plugin was restarted.
        Bukkit.getOnlinePlayers().forEach(phaseFiveCommand::recoverOnJoin);
    }

    private org.bukkit.World generationWorld() {
        org.bukkit.World world = getServer().getWorld(generationWorldName);
        if (world == null) throw new IllegalStateException("generation world is not loaded: " + generationWorldName);
        return world;
    }

    private void notifyCombatRoom(CombatRoomService.RoomNotice notice) {
        generation.info(notice.instanceId()).ifPresent(instance -> instance.participants().forEach(playerId -> {
            org.bukkit.entity.Player player = getServer().getPlayer(playerId);
            if (player == null) return;
            String message = notice.unlockedRoom() < 0
                    ? "<green>Room <white>" + notice.clearedRoom() + "</white> cleared.</green>"
                    : "<green>Room <white>" + notice.clearedRoom()
                    + "</white> cleared. <yellow>Door to room <white>" + notice.unlockedRoom()
                    + "</white> unlocked.</yellow></green>";
            player.sendMessage(MiniMessageUtils.miniMessage(message));
        }));
    }

    private void handleLifecycleNotice(PlayerLifecycleService.Notice notice) {
        Player player = notice.playerId() == null ? null : getServer().getPlayer(notice.playerId());
        switch (notice.event()) {
            case GHOSTED -> {
                if (player == null) return;
                BukkitGhostState.enter(player);
                showLifecycleTitle(player, "", "<yellow>" + notice.detail() + "</yellow>", 0, 30, 5);
                player.sendMessage(MiniMessageUtils.miniMessage(
                        "<gray>You are a ghost. You will revive in 60 seconds if the run remains active.</gray>"));
            }
            case GHOST_COUNTDOWN, RECONNECTED -> {
                if (player == null || notice.reviveAt() == null) return;
                BukkitGhostState.refresh(player);
                showLifecycleTitle(player, "", "<yellow>" + notice.detail() + "</yellow>", 0, 25, 5);
            }
            case REVIVED -> {
                if (player == null) {
                    getLogger().warning("REVIVED player is offline or unknown: " + notice.playerId());
                    return;
                }
                Player target = notice.reviveTarget() == null ? null : getServer().getPlayer(notice.reviveTarget());
                if (target != null && !player.teleport(target.getLocation().clone().add(0, 1, 0))) {
                    getLogger().warning("Unable to teleport revived player " + player.getName()
                            + " near target " + target.getName());
                    return;
                }
                healToFull(player);
                BukkitGhostState.exit(player);
                scheduleReviveHeal(notice.instanceId(), player, 1L);
                scheduleReviveHeal(notice.instanceId(), player, 20L);
                showLifecycleTitle(player, "<green>Revived</green>", "<white>Welcome back</white>", 5, 40, 10);
                player.sendMessage(MiniMessageUtils.miniMessage("<green>You have been revived.</green>"));
            }
            case REMOVED -> {
                if (player != null) BukkitGhostState.exit(player);
                if (phaseFiveCommand != null && notice.playerId() != null) {
                    phaseFiveCommand.restoreRemovedPlayer(notice.instanceId(), notice.playerId());
                }
            }
            case WIPED -> {
                lifecycle.info(notice.instanceId()).ifPresent(snapshot -> snapshot.players().stream()
                        .filter(value -> value.state() == PlayerLifecycleService.PlayerState.GHOST)
                        .map(value -> getServer().getPlayer(value.playerId()))
                        .filter(java.util.Objects::nonNull)
                        .forEach(BukkitGhostState::exit));
                if (phaseFiveCommand != null) {
                    phaseFiveCommand.wipeFromLifecycle(notice.instanceId(), notice.detail());
                }
            }
            default -> { }
        }
    }

    private static void showLifecycleTitle(Player player, String title, String subtitle,
                                           int fadeIn, int stay, int fadeOut) {
        player.showTitle(Title.title(MiniMessageUtils.miniMessage(title),
                MiniMessageUtils.miniMessage(subtitle), fadeIn, stay, fadeOut));
    }

    private void healToFull(Player player) {
        StatsManager.healPlayerPercent(player, 100D);
    }

    private void scheduleReviveHeal(UUID instanceId, Player player, long delay) {
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && lifecycle.player(instanceId, player.getUniqueId())
                    .map(value -> value.state() == PlayerLifecycleService.PlayerState.ALIVE).orElse(false)) {
                healToFull(player);
            }
        }, delay);
    }

    private void startTasks() {
        getServer().getScheduler().runTaskTimer(this, (Runnable) centralUpdates::tick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        disabling = true;
        if (progressBars != null) progressBars.cancelAll();
        if (lifecycle != null) {
            lifecycle.instances().stream()
                    .flatMap(instance -> instance.players().stream())
                    .filter(value -> value.state() == PlayerLifecycleService.PlayerState.GHOST)
                    .map(value -> getServer().getPlayer(value.playerId()))
                    .filter(java.util.Objects::nonNull)
                    .forEach(BukkitGhostState::exit);
            lifecycle.cleanupAll();
        }
        if (phaseSeven != null) phaseSeven.cleanupAll();
        if (phaseNine != null) phaseNine.cleanupAll();
        if (combat != null) combat.cleanupAll();
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
