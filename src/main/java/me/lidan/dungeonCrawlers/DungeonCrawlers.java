package me.lidan.dungeonCrawlers;

import dev.triumphteam.gui.guis.BaseGui;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.cavecrawlers.stats.StatsManager;
import me.lidan.dungeonCrawlers.commands.DungeonCrawlersCommand;
import me.lidan.dungeonCrawlers.commands.DungeonAuthoringCommand;
import me.lidan.dungeonCrawlers.commands.DungeonGenerationCommand;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseFiveCommand;
import me.lidan.dungeonCrawlers.commands.DungeonClassMenuService;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseSixCommand;
import me.lidan.dungeonCrawlers.commands.ClassIdSuggestionProvider;
import me.lidan.dungeonCrawlers.commands.DifficultyIdSuggestionProvider;
import me.lidan.dungeonCrawlers.commands.FloorIdSuggestionProvider;
import me.lidan.dungeonCrawlers.commands.InstanceIdSuggestionProvider;
import me.lidan.dungeonCrawlers.commands.OfflinePlayerSuggestionProvider;
import me.lidan.dungeonCrawlers.commands.RoomIdSuggestionProvider;
import me.lidan.dungeonCrawlers.authoring.TemplateAuthoringService;
import me.lidan.dungeonCrawlers.authoring.TemplateCatalogLoader;
import me.lidan.dungeonCrawlers.compatibility.CompatibilityService;
import me.lidan.dungeonCrawlers.config.DungeonTimings;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.config.registry.EncounterRegistry;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.core.reservation.PlayerReservationService;
import me.lidan.dungeonCrawlers.core.claim.RewardClaimService;
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
import me.lidan.dungeonCrawlers.core.reward.RewardEntitlementService;
import me.lidan.dungeonCrawlers.core.score.ScoreService;
import me.lidan.dungeonCrawlers.core.score.ScoreResultRenderer;
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
import me.lidan.dungeonCrawlers.commands.DungeonPhaseElevenCommand;
import me.lidan.dungeonCrawlers.commands.RewardIdSuggestionProvider;
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
import me.lidan.dungeonCrawlers.integration.BukkitRewardChestListener;
import me.lidan.dungeonCrawlers.integration.BukkitRewardMailboxListener;
import me.lidan.dungeonCrawlers.integration.BukkitReloadProtectionListener;
import me.lidan.dungeonCrawlers.integration.ThrottledDungeonActionBar;
import me.lidan.dungeonCrawlers.integration.BukkitWorldProtectionListener;
import me.lidan.dungeonCrawlers.integration.BukkitDungeonRunListener;
import me.lidan.dungeonCrawlers.integration.BukkitDungeonLifecycleListener;
import me.lidan.dungeonCrawlers.integration.BukkitDungeonActionBar;
import me.lidan.dungeonCrawlers.integration.BukkitGhostState;
import me.lidan.dungeonCrawlers.integration.ClassSelectorNpcService;
import me.lidan.dungeonCrawlers.integration.NoOpClassSelectorNpcService;
import me.lidan.dungeonCrawlers.integration.DebugSettings;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import me.lidan.dungeonCrawlers.integration.DungeonPlaceholderExpansion;
import me.lidan.dungeonCrawlers.authoring.RoomMarkerItemFactory;
import me.lidan.dungeonCrawlers.integration.mythic.MythicMobsAdapter;
import me.lidan.dungeonCrawlers.integration.cave.CaveActionBarAdapter;
import me.lidan.dungeonCrawlers.integration.cave.CaveItemsAdapter;
import me.lidan.dungeonCrawlers.integration.parties.PartyProviders;
import me.lidan.dungeonCrawlers.integration.worldedit.FaweGenerationAdapter;
import me.lidan.dungeonCrawlers.integration.worldedit.JigsawMarkerPlacementListener;
import me.lidan.dungeonCrawlers.integration.worldedit.WorldEditAdapter;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.FileDurableRepository;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DungeonCrawlers extends JavaPlugin {
    private me.lidan.dungeonCrawlers.integration.BukkitFoundryRooms foundryRooms;
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
    private DungeonClassMenuService classMenuService;
    private ClassSelectorNpcService classSelectorNpcs;
    private RoomMarkerItemFactory roomMarkerItems;
    private BukkitProgressBarService progressBars;
    private SecretDiscoveryService phaseSeven;
    private PortalEncounterService phaseNine;
    private DungeonPhaseElevenCommand phaseElevenCommand;
    private RewardEntitlementService rewards;
    private RewardClaimService claims;
    private BukkitBossIdentity bossIdentity;
    private MythicMobsAdapter mythicMobs;
    private PlayerLifecycleService lifecycle;
    private me.lidan.dungeonCrawlers.core.difficulty.DungeonProgressionService progression;
    private me.lidan.dungeonCrawlers.integration.BukkitDifficultyService difficultyService;
    private me.lidan.dungeonCrawlers.integration.BukkitClassAbilityService classAbilities;
    private me.lidan.dungeonCrawlers.integration.cave.DungeonSupportItems supportItems;
    private DebugSettings debugSettings;
    private DungeonPlaceholderExpansion placeholderExpansion;
    private DungeonTimings timings;
    private ScoreService scoreService;
    private final Map<UUID, ScoreService.FinalScoreSnapshot> latestScores = new ConcurrentHashMap<>();
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
        timings = configuredTimings();
        scoreService = new ScoreService(timings.scoreFreeTime(), timings.scorePenaltyInterval());
        debugSettings = new DebugSettings(configuredBoolean("debug", false));
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
                callback -> getServer().getScheduler().runTask(this, callback), timings.persistenceShutdownGrace());
        initializePhaseThreeServices();
        getLogger().info("Loaded DungeonCrawlers configuration: floors=" + loaded.snapshot().floors().size()
                + ", rooms=" + loaded.snapshot().rooms().size() + ", bossEncounters="
                + loaded.snapshot().encounters().size() + ", hash=" + loaded.snapshot().hash());
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
                this::generationDiagnostic, phaseClock(), worldName, progress -> {
                    if (progress.terminal()) {
                        if (progress.successful()) progressBars.complete(progress.instanceId(), progress.detail());
                        else progressBars.fail(progress.instanceId(), progress.detail());
                    } else {
                        progressBars.update(progress.instanceId(), progress.progress(), progress.detail());
                    }
                }, timings.generationCleanupDeadline());
        generation.recover();
        initializePhaseFourServices(worldName);
    }

    private java.time.Clock phaseClock() {
        if (phaseClock == null) phaseClock = Clock.systemUTC();
        return phaseClock;
    }

    private void initializePhaseFourServices(String worldName) {
        centralUpdates = new CentralUpdateService(phaseClock(), this::diagnosticWarning);
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
        var combatGateway = new BukkitCombatMobGateway(getServer(), this::generationWorld, mythicMobs, entityIdentity);
        combat = new CombatRoomService(combatGateway,
                chunkTickets, this::diagnosticWarning, this::notifyCombatRoom);
        runPreparation = new RunPreparationService(doors, centralUpdates, new StateTransitionService(), phaseClock(),
                instanceId -> {
                    var result = combat.activateFirst(instanceId);
                    if (!result.successful()) {
                        throw new IllegalStateException(result.detail());
                    }
                    debugLog("instance=" + instanceId + " first room activated");
                }, getLogger()::warning, instanceId -> {
                    cancelDeadlineInstance(instanceId);
                }, true, timings);
        var classPreferences = new me.lidan.dungeonCrawlers.integration.BukkitClassPreferences(this);
        runPreparation.configureClassPreferences(classPreferences::read, classPreferences::write);
        phaseSeven = new SecretDiscoveryService(configRegistry::snapshot);
        lifecycle = new PlayerLifecycleService(centralUpdates, phaseClock(), this::handleLifecycleNotice, timings);
        bossIdentity = new BukkitBossIdentity(this);
        var bossGateway = new BukkitBossGateway(getServer(), this::generationWorld, mythicMobs, bossIdentity);
        var encounterFactories = EncounterFactoryRegistry.withBasic();
        encounterFactories.register(me.lidan.dungeonCrawlers.core.encounter.RingmasterEncounter.ID, context ->
                new me.lidan.dungeonCrawlers.core.encounter.RingmasterEncounter(context,
                        new me.lidan.dungeonCrawlers.integration.BukkitRingmasterArena(context, this,
                                runPreparation, lifecycle, mythicMobs, difficultyService), phaseClock()));
        encounterFactories.register(me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.ID, context -> {
            boolean impossible = generation.layoutContext(context.instanceId()).orElseThrow().difficulty().tier()
                    == me.lidan.dungeonCrawlers.core.difficulty.Difficulty.IMPOSSIBLE;
            try {
                var settings = me.lidan.dungeonCrawlers.config.FoundryPack.load(configFactory,
                        getDataFolder().toPath().resolve("foundry.yml"));
                return new me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter(context,
                        new me.lidan.dungeonCrawlers.integration.BukkitChainboundArena(context, this, generation,
                                runPreparation, lifecycle, teleportPermits, phaseClock()), settings, impossible, phaseClock());
            } catch (IOException exception) { throw new IllegalStateException(exception); }
        });
        phaseNine = new PortalEncounterService(centralUpdates, runPreparation,
                encounterFactories,
                bossGateway,
                new BukkitPortalParticipantGateway(getServer(), this::generationWorld, generationWorldName,
                        runPreparation, lifecycle, combat, teleportPermits, phaseClock(), timings.teleportPermit()),
                phaseClock(), getLogger()::warning, this::finalizeRewards, timings);
        rewards = new RewardEntitlementService(phaseClock(), new CaveItemsAdapter()::isConfigured,
                durableRepository, timings);
        rewards.configureLiveAccess(instanceId -> runPreparation.info(instanceId).isPresent());
        claims = new RewardClaimService(phaseClock(), durableRepository, rewards, new CaveItemsAdapter(),
                () -> {
                    RegisteredServiceProvider<Economy> registration = getServer().getServicesManager()
                            .getRegistration(Economy.class);
                    return registration == null ? null : new me.lidan.dungeonCrawlers.integration.vault.VaultEconomyAdapter(
                            registration.getProvider());
                }, callback -> getServer().getScheduler().runTask(this, callback),
                detail -> getLogger().warning(detail));
        progression = new me.lidan.dungeonCrawlers.core.difficulty.DungeonProgressionService(durableRepository);
        difficultyService = new me.lidan.dungeonCrawlers.integration.BukkitDifficultyService(
                this, generation, runPreparation, lifecycle, claims);
        combatGateway.configureDifficulty(difficultyService::spawn);
        bossGateway.configureDifficulty(difficultyService::spawn);
        foundryRooms = new me.lidan.dungeonCrawlers.integration.BukkitFoundryRooms(this, generation, runPreparation,
                lifecycle, combat, teleportPermits, phaseClock(), worldName);
        combat.configureObjectives(foundryRooms::complete, foundryRooms::cleanup);
        getServer().getPluginManager().registerEvents(foundryRooms, this);
        lifecycle.configureRevival(difficultyService::activeRunicPet, this::applyRevival);
        runPreparation.configureCombatStarted(instance -> {
            var context = generation.layoutContext(instance).orElseThrow();
            if (context.progressionEnabled()) runPreparation.info(instance).orElseThrow().participants().forEach(
                    player -> progression.begin(instance, player, context.floor(), context.difficulty()));
        });
        runPreparation.configureDeadlineHandlers(this::handleRunFailure, this::hasActiveCompletionGroup,
                this::handleDeadlineNotice);
        registerPlaceholderExpansion();
    }

    private void registerPlaceholderExpansion() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        placeholderExpansion = new DungeonPlaceholderExpansion(this, generation, runPreparation, lifecycle, phaseSeven,
                combat, debugSettings, latestScores::get);
        if (!placeholderExpansion.register()) {
            placeholderExpansion = null;
            getLogger().warning("PlaceholderAPI hook could not be registered");
            return;
        }
        getLogger().info("PlaceholderAPI hook enabled (dungeoncrawlers)");
    }

    private void cancelDeadlineInstance(UUID instanceId) {
        if (phaseFiveCommand != null) {
            phaseFiveCommand.closeFromDeadline(instanceId);
            return;
        }
        if (lifecycle != null) lifecycle.cleanup(instanceId);
        if (phaseSeven != null) phaseSeven.cleanup(instanceId);
        if (phaseNine != null) phaseNine.cleanup(instanceId);
        if (combat != null) combat.cleanup(instanceId);
        generation.cancel(instanceId);
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

    private DungeonTimings configuredTimings() {
        DungeonTimings defaults = DungeonTimings.defaults();
        return new DungeonTimings(
                configuredSeconds("timings.preparation-warning-seconds", defaults.preparationWarning()),
                configuredSeconds("timings.preparation-timeout-seconds", defaults.preparationTimeout()),
                configuredSeconds("timings.run-warning-seconds", defaults.runWarning()),
                configuredSeconds("timings.run-timeout-seconds", defaults.runTimeout()),
                configuredSeconds("timings.failed-reading-period-seconds", defaults.failedReadingPeriod()),
                configuredSeconds("timings.completion-warning-seconds", defaults.completionWarning()),
                configuredSeconds("timings.completion-timeout-seconds", defaults.completionTimeout()),
                configuredSeconds("timings.completion-final-countdown-seconds", defaults.completionFinalCountdown()),
                configuredSeconds("timings.revive-seconds", defaults.reviveDuration()),
                configuredSeconds("timings.admin-revive-seconds", defaults.adminReviveDuration()),
                configuredSeconds("timings.portal-countdown-seconds", defaults.portalCountdown()),
                configuredSeconds("timings.boss-spawn-delay-seconds", defaults.bossSpawnDelay()),
                configuredSeconds("timings.live-reward-window-seconds", defaults.liveRewardWindow()),
                configuredSeconds("timings.recovered-reward-window-seconds", defaults.recoveredRewardWindow()),
                configuredSeconds("timings.recovered-reward-session-seconds",
                        defaults.recoveredRewardSessionWindow()),
                configuredSeconds("timings.score-free-time-seconds", defaults.scoreFreeTime()),
                configuredSeconds("timings.score-penalty-interval-seconds", defaults.scorePenaltyInterval()),
                configuredSeconds("timings.generation-cleanup-deadline-seconds",
                        defaults.generationCleanupDeadline()),
                configuredSeconds("timings.teleport-permit-seconds", defaults.teleportPermit()),
                configuredSeconds("timings.action-bar-cooldown-seconds", defaults.actionBarCooldown()),
                configuredSeconds("timings.persistence-shutdown-grace-seconds",
                        defaults.persistenceShutdownGrace()),
                configuredSeconds("timings.pending-recovery-max-age-seconds", defaults.pendingRecoveryMaxAge()));
    }

    private Duration configuredSeconds(String route, Duration defaultValue) {
        if (!mainConfig.contains(route, true)) return defaultValue;
        Object value = mainConfig.get(route);
        try {
            long seconds = value instanceof Number number ? new BigDecimal(number.toString()).longValueExact() : -1;
            if (seconds < 1 || seconds > Duration.ofDays(365).toSeconds()) throw new ArithmeticException();
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalStateException("config.yml " + route
                    + " must be an integer number of seconds in 1..31536000");
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

    private boolean configuredBoolean(String route, boolean defaultValue) {
        if (!mainConfig.contains(route, true)) return defaultValue;
        Object value = mainConfig.get(route);
        if (value instanceof Boolean enabled) return enabled;
        throw new IllegalStateException("config.yml " + route + " must be true or false");
    }

    private void registerSerializer() {
        // Register custom serializers if needed
    }

    private void saveDefaultResources() {
        saveDefaultResourceIfMissing("classes.yml");
        saveDefaultResourceIfMissing("blessings.yml");
        saveDefaultResourceIfMissing("rooms.yml");
        saveDefaultResourceIfMissing("difficulties.yml");
        saveDefaultResourceIfMissing("floors/floor_1.yml");
        saveDefaultResourceIfMissing("floors/floor_3.yml");
        saveDefaultResourceIfMissing("rooms_foundry.yml");
        saveDefaultResourceIfMissing("foundry.yml");
        try { me.lidan.dungeonCrawlers.config.FoundryPack.installTemplates(this); }
        catch (IOException exception) { throw new IllegalStateException("Cannot install Foundry templates", exception); }
        saveDefaultResourceIfMissing("config.yml");
    }

    private void saveDefaultResourceIfMissing(String resource) {
        if (new File(getDataFolder(), resource).exists()) return;
        saveResource(resource, false);
    }

    private void migrateVersionedDataConfigs() throws IOException {
        Path classesPath = getDataFolder().toPath().resolve("classes.yml");
        try {
            configFactory.openVersionedConfig(classesPath, "classes.yml", 2);
        } finally {
            configFactory.release(classesPath);
        }
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
        me.lidan.dungeonCrawlers.commands.AdminSuggestionProviders.register(commandHandlerBuilder, combat,
                runPreparation, new CaveItemsAdapter()::ids, mythicMobs::ids,
                difficultyService::enemyIds, claims::reconciliationIds);
        commandHandlerBuilder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
            if (annotation.value() != DifficultyIdSuggestionProvider.class) return null;
            return new DifficultyIdSuggestionProvider<BukkitCommandActor>();
        });
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
        commandHandlerBuilder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
            if (annotation.value() != RewardIdSuggestionProvider.class) return null;
            return new RewardIdSuggestionProvider<BukkitCommandActor>(() -> configRegistry.snapshot().floors().values()
                    .stream().flatMap(floor -> floor.rewards().keySet().stream()).toList());
        });
        var dungeonActionBar = new ThrottledDungeonActionBar(
                new BukkitDungeonActionBar(new CaveActionBarAdapter()), phaseClock(), timings.actionBarCooldown());
        phaseFiveCommand = new DungeonPhaseFiveCommand(configRegistry, PartyProviders.forServer(getServer()),
                generation, runPreparation, playerSnapshots, teleportPermits, getServer(), this, phaseClock(),
                generationWorldName,
                dungeonActionBar,
                new DungeonPhaseFiveCommand.PhaseServices(combat, progressBars, phaseSeven, lifecycle, phaseNine),
                timings);
        classMenuService = new DungeonClassMenuService(configRegistry, runPreparation,
                phaseFiveCommand::renderDoor, dungeonActionBar);
        phaseFiveCommand.setClassMenuService(classMenuService);
        phaseFiveCommand.configureDifficulties(progression, instance -> recordProgression(instance, false, null));
        classSelectorNpcs = createClassSelectorNpcService();
        phaseFiveCommand.setClassSelectorNpcService(classSelectorNpcs);
        roomMarkerItems = new RoomMarkerItemFactory(this);
        Lamp<BukkitCommandActor> commandHandler = commandHandlerBuilder.build();
        commandHandler.register(new DungeonCrawlersCommand(this,
                new CompatibilityService(this, mainConfig, configRegistry), mainConfig, configRegistry,
                reservations, durableRepository, generation, phaseFiveCommand::cancelFromAdmin,
                this::hasCompletionPending, debugSettings::enabled, debugSettings::setEnabled, scoreService));
        commandHandler.register(new DungeonAuthoringCommand(this, mainConfig, configRegistry, reservations, authoring,
                generation::activeTemplateIds, progressBars));
        var roomPreview = new me.lidan.dungeonCrawlers.commands.DungeonRoomPreviewCommand(configRegistry,
                generation, phaseFiveCommand, getServer(), generationWorldName, teleportPermits, phaseClock(),
                timings.teleportPermit(), callback -> getServer().getScheduler().runTaskLater(this, callback, 3L), progressBars);
        commandHandler.register(roomPreview);
        registerEvent(roomPreview);
        commandHandler.register(new DungeonGenerationCommand(configRegistry,
                PartyProviders.forServer(getServer()), generation, getServer(),
                generationWorldName,
                teleportPermits, phaseClock(), phaseFiveCommand::cancelFromAdmin, runPreparation,
                debugSettings::enabled, lifecycle, phaseSeven, latestScores::get, combat, phaseNine,
                timings.teleportPermit()));
        commandHandler.register(phaseFiveCommand);
        commandHandler.register(new me.lidan.dungeonCrawlers.commands.DungeonProgressionCommand(configRegistry, progression));
        commandHandler.register(new me.lidan.dungeonCrawlers.commands.DungeonDifficultyDebugCommand(
                difficultyService, debugSettings::enabled, runPreparation, phaseFiveCommand));
        commandHandler.register(new DungeonPhaseSixCommand(combat, runPreparation, debugSettings::enabled));
        commandHandler.register(new DungeonPhaseSevenCommand(phaseSeven, runPreparation, debugSettings::enabled));
        commandHandler.register(new DungeonPhaseEightCommand(lifecycle, runPreparation, phaseFiveCommand,
                debugSettings::enabled));
        commandHandler.register(new DungeonPhaseNineCommand(phaseNine, runPreparation,
                phaseFiveCommand::cancelFromAdmin));
        phaseElevenCommand = new DungeonPhaseElevenCommand(rewards, generation, runPreparation, configRegistry,
                lifecycle, claims, debugSettings::enabled, scoreService);
        commandHandler.register(phaseElevenCommand);
        commandHandler.register(new DungeonPhaseFourCommand(centralUpdates, doors, protectionPolicy,
                teleportPermits, playerSnapshots, getServer(), this, phaseClock(),
                generationWorldName,
                () -> generation.protectionRegions().stream().map(WorldProtectionService.InstanceRegion::from).toList(),
                runPreparation, debugSettings::enabled, timings.teleportPermit()));
    }

    private void registerEvents() {
        BukkitRewardMailboxListener rewardMailboxListener = new BukkitRewardMailboxListener(claims);
        registerEvent(new BukkitWorldProtectionListener(protectionPolicy,
                () -> generation.protectionRegions().stream().map(WorldProtectionService.InstanceRegion::from).toList(),
                teleportPermits, phaseClock(), (instance, point) -> runPreparation.doorAt(point).isPresent()
                        || combat.isDoorAt(point) || phaseNine.rewardAt(point).isPresent()
                        || phaseSeven.secrets(instance).stream().anyMatch(secret -> secret.worldPoint().equals(point))));
        registerEvent(new BukkitDungeonRunListener(phaseFiveCommand, runPreparation, generationWorldName, phaseSeven));
        registerEvent(difficultyService);
        classAbilities = new me.lidan.dungeonCrawlers.integration.BukkitClassAbilityService(this,
                runPreparation, lifecycle, generation, configRegistry, entityIdentity, bossIdentity,
                difficultyService, phaseClock(), generationWorldName);
        registerEvent(classAbilities);
        supportItems = new me.lidan.dungeonCrawlers.integration.cave.DungeonSupportItems(this,
                classAbilities::activateSupport);
        supportItems.register();
        registerEvent(new BukkitDungeonLifecycleListener(lifecycle, runPreparation, this, phaseClock(),
                generationWorldName, phaseFiveCommand::recoverOnJoin, phaseFiveCommand::leaveFromDungeon));
        registerEvent(new BukkitCombatListener(combat, entityIdentity, generationWorldName, () -> disabling,
                bossIdentity, phaseNine, phaseFiveCommand::canOpenDungeonDoor));
        registerEvent(new BukkitPortalBossListener(this, phaseNine, runPreparation, generationWorldName));
        registerEvent(new BukkitRewardChestListener(phaseNine, generationWorldName,
                phaseElevenCommand::openRewards, phaseFiveCommand::canOpenDungeonDoor));
        registerEvent(rewardMailboxListener);
        registerEvent(new BukkitReloadProtectionListener(this::hasCompletionPending));
        registerEvent(classMenuService);
        registerEvent(new JigsawMarkerPlacementListener(roomMarkerItems));
        if (classSelectorNpcs instanceof Listener listener) registerEvent(listener);
        // PlugMan-style reloads do not emit PlayerJoinEvent; repair any durable snapshots for players
        // who stayed online while the plugin was restarted.
        Bukkit.getOnlinePlayers().forEach(phaseFiveCommand::recoverOnJoin);
        Bukkit.getOnlinePlayers().forEach(rewardMailboxListener::recover);
    }

    private org.bukkit.World generationWorld() {
        org.bukkit.World world = getServer().getWorld(generationWorldName);
        if (world == null) throw new IllegalStateException("generation world is not loaded: " + generationWorldName);
        return world;
    }

    private boolean finalizeRewards(PortalEncounterService.Snapshot snapshot) {
        var context = generation.layoutContext(snapshot.instanceId()).orElse(null);
        var run = runPreparation.info(snapshot.instanceId()).orElse(null);
        var lifecycleSnapshot = lifecycle.info(snapshot.instanceId()).orElse(null);
        org.bukkit.World world = getServer().getWorld(generationWorldName);
        if (context == null || run == null || lifecycleSnapshot == null || world == null) return false;
        if (!lifecycle.complete(snapshot.instanceId()).successful()) return false;
        List<RewardEntitlementService.Participant> participants = rewardParticipants(run, lifecycleSnapshot);
        ScoreService.ScoreReport score = calculateScore(run, lifecycleSnapshot, true, phaseClock().instant());
        latestScores.put(snapshot.instanceId(), score.finalSnapshot());
        rewards.register(new RewardEntitlementService.Completion(snapshot.instanceId(),
                context.seed(), phaseClock().instant(), score.finalSnapshot(), participants,
                context.floor().rewards(), context.difficulty().tier()));
        recordProgression(snapshot.instanceId(), true, null);
        var point = snapshot.rewardChest();
        world.getBlockAt(point.x(), point.y(), point.z()).setType(org.bukkit.Material.ENDER_CHEST, false);
        participants.stream().map(RewardEntitlementService.Participant::playerId)
                .map(getServer()::getPlayer)
                .filter(java.util.Objects::nonNull)
                .forEach(player -> DungeonMessages.send(player, ScoreResultRenderer.render(score,
                        scoreService.freeTime(), scoreService.penaltyInterval())));
        return true;
    }

    private void handleRunFailure(UUID instanceId) {
        recordProgression(instanceId, false, null);
        if (phaseNine != null) phaseNine.cleanup(instanceId);
        var context = generation.layoutContext(instanceId).orElse(null);
        var run = runPreparation.info(instanceId).orElse(null);
        var lifecycleSnapshot = lifecycle == null ? null : lifecycle.info(instanceId).orElse(null);
        if (context == null || run == null || lifecycleSnapshot == null) {
            getLogger().warning("instance=" + instanceId + " failed run could not be scored before cleanup");
            return;
        }
        Instant failedAt = run.failedDeadline() == null
                ? phaseClock().instant()
                : run.failedDeadline().minus(timings.failedReadingPeriod());
        try {
            ScoreService.ScoreReport score = calculateScore(run, lifecycleSnapshot, false, failedAt);
            latestScores.put(instanceId, score.finalSnapshot());
            List<RewardEntitlementService.Participant> participants = rewardParticipants(run, lifecycleSnapshot);
            rewards.register(new RewardEntitlementService.Completion(instanceId, context.seed(), failedAt,
                    score.finalSnapshot(), participants, context.floor().rewards(), context.difficulty().tier()));
            run.participants().stream().map(getServer()::getPlayer).filter(java.util.Objects::nonNull)
                    .forEach(player -> DungeonMessages.send(player, ScoreResultRenderer.render(score,
                            scoreService.freeTime(), scoreService.penaltyInterval())));
        } catch (RuntimeException exception) {
            getLogger().warning("instance=" + instanceId + " failed result persistence failed: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private List<RewardEntitlementService.Participant> rewardParticipants(
            RunPreparationService.RunSnapshot run, PlayerLifecycleService.InstanceSnapshot lifecycleSnapshot) {
        var lifecyclePlayers = lifecycleSnapshot.players().stream().collect(
                java.util.stream.Collectors.toMap(PlayerLifecycleService.PlayerSnapshot::playerId, value -> value));
        return run.participants().stream()
                .map(lifecyclePlayers::get)
                .filter(java.util.Objects::nonNull)
                .filter(player -> player.state() != PlayerLifecycleService.PlayerState.REMOVED)
                .map(player -> new RewardEntitlementService.Participant(player.playerId(), true,
                        getServer().getPlayer(player.playerId()) != null,
                        difficultyService.magicFind(run.instanceId(), player.playerId())))
                .toList();
    }

    private ScoreService.ScoreReport calculateScore(RunPreparationService.RunSnapshot run,
                                                     PlayerLifecycleService.InstanceSnapshot lifecycleSnapshot,
                                                     boolean successful, Instant endedAt) {
        var lifecyclePlayers = lifecycleSnapshot.players().stream().collect(
                java.util.stream.Collectors.toMap(PlayerLifecycleService.PlayerSnapshot::playerId, value -> value));
        int totalSecrets = phaseSeven.info(run.instanceId()).map(value -> value.secrets().size()).orElse(0);
        int foundSecrets = phaseSeven.info(run.instanceId()).map(value -> (int) value.secrets().stream()
                .filter(SecretDiscoveryService.SecretSnapshot::discovered).count()).orElse(0);
        int deaths = run.participants().stream().map(lifecyclePlayers::get).filter(java.util.Objects::nonNull)
                .mapToInt(PlayerLifecycleService.PlayerSnapshot::deaths).sum();
        boolean runicPet = run.participants().stream().map(lifecyclePlayers::get)
                .filter(java.util.Objects::nonNull)
                .filter(player -> player.state() != PlayerLifecycleService.PlayerState.REMOVED)
                .anyMatch(player -> difficultyService.activeRunicPet(player.playerId()));
        Duration elapsed = run.startedAt() == null ? Duration.ZERO
                : Duration.between(run.startedAt(), endedAt);
        if (elapsed.isNegative()) elapsed = Duration.ZERO;
        return scoreService.calculateReport(
                new ScoreService.ScoreInput(successful, deaths, elapsed, foundSecrets, totalSecrets),
                List.of(new ScoreService.RunicPetBonus(runicPet),
                        new ScoreService.RunicBossBonus(difficultyService.runicBoss(run.instanceId()))));
    }

    private boolean hasActiveCompletionGroup(UUID instanceId) {
        return lifecycle != null && lifecycle.info(instanceId).map(snapshot -> snapshot.players().stream()
                .anyMatch(player -> player.online() && (player.state() == PlayerLifecycleService.PlayerState.ALIVE
                        || player.state() == PlayerLifecycleService.PlayerState.GHOST
                        && player.reviveKind() == PlayerLifecycleService.ReviveKind.RUNIC)))
                .orElse(false);
    }

    private boolean hasCompletionPending() {
        return runPreparation != null && runPreparation.snapshots().stream()
                .anyMatch(snapshot -> snapshot.state() == RunPreparationService.RunState.COMPLETION_PENDING);
    }

    private void handleDeadlineNotice(RunPreparationService.DeadlineNotice notice) {
        RunPreparationService.RunSnapshot run = runPreparation.info(notice.instanceId()).orElse(null);
        if (run == null) return;
        List<Player> players = run.participants().stream().map(getServer()::getPlayer)
                .filter(java.util.Objects::nonNull).toList();
        switch (notice.event()) {
            case PREPARATION_WARNING -> notifyDeadline(players,
                    "<yellow>Class selection closes in <white>" + formatSeconds(notice.secondsRemaining())
                            + "</white>.</yellow>");
            case RUN_WARNING -> notifyDeadline(players,
                    "<yellow>Dungeon time limit expires in <white>" + formatSeconds(notice.secondsRemaining())
                            + "</white>.</yellow>");
            case RUN_FAILED -> {
                notifyDeadline(players, "<red>" + runFailureMessage(notice.detail()) + "</red>");
                players.forEach(player -> showLifecycleTitle(player, "<red>Dungeon Failed</red>",
                        "<yellow>Reading period: " + formatSeconds(timings.failedReadingPeriod().toSeconds())
                                + "</yellow>", 5, 40, 10));
            }
            case COMPLETION_WARNING -> notifyDeadline(players,
                    "<yellow>Reward chest closes in <white>" + formatSeconds(notice.secondsRemaining())
                            + "</white>.</yellow>");
            case COMPLETION_COUNTDOWN -> players.forEach(player -> showLifecycleTitle(player,
                    "<red>Reward chest closing</red>", "<yellow>In <white>" + notice.secondsRemaining()
                            + "</white> seconds</yellow>", 0, 25, 5));
            default -> { }
        }
    }

    private static void notifyDeadline(List<Player> players, String message) {
        players.forEach(player -> DungeonMessages.send(player, message));
    }

    private static String formatSeconds(long seconds) {
        long safeSeconds = Math.max(0, seconds);
        long minutes = safeSeconds / 60;
        long remainder = safeSeconds % 60;
        if (minutes > 0 && remainder == 0) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        if (minutes > 0) return minutes + "m " + remainder + "s";
        return safeSeconds + (safeSeconds == 1 ? " second" : " seconds");
    }

    private static String runFailureMessage(String detail) {
        String normalized = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        if (normalized.contains("time limit")) return "Dungeon failed: the time limit was reached.";
        if (normalized.contains("no online active alive player")) {
            return "Dungeon failed: no active players remain.";
        }
        return "Dungeon failed. The run has entered its reading period.";
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
            DungeonMessages.send(player, message);
        }));
    }

    private void handleLifecycleNotice(PlayerLifecycleService.Notice notice) {
        Player player = notice.playerId() == null ? null : getServer().getPlayer(notice.playerId());
        switch (notice.event()) {
            case GHOSTED -> {
                if (player == null) return;
                BukkitGhostState.enter(player, notice.reviveAt() == null ? Duration.ofMillis(Integer.MAX_VALUE * 50L)
                        : remainingGhostDuration(notice.reviveAt()));
                showLifecycleTitle(player, "", "<yellow>" + notice.detail() + "</yellow>", 0, 30, 5);
                DungeonMessages.send(player,
                        "<gray>You are a ghost. " + notice.detail()
                                + " if the run remains active.</gray>");
            }
            case GHOST_COUNTDOWN, RECONNECTED -> {
                if (player == null || notice.reviveAt() == null) return;
                BukkitGhostState.refresh(player, remainingGhostDuration(notice.reviveAt()));
                showLifecycleTitle(player, "", "<yellow>" + notice.detail() + "</yellow>", 0, 25, 5);
            }
            case REVIVED -> {
                if (player == null) {
                    getLogger().warning("REVIVED player is offline or unknown: " + notice.playerId());
                    return;
                }
                showLifecycleTitle(player, "<green>Revived</green>", "<white>Welcome back</white>", 5, 40, 10);
                DungeonMessages.send(player, "<green>You have been revived.</green>");
            }
            case REMOVED -> {
                recordProgression(notice.instanceId(), false, notice.playerId());
                if (player != null) BukkitGhostState.exit(player);
                if (phaseFiveCommand != null && notice.playerId() != null) {
                    if (shouldRestoreRemovedPlayer(player, generationWorldName)) {
                        phaseFiveCommand.restoreRemovedPlayer(notice.instanceId(), notice.playerId());
                    } else {
                        phaseFiveCommand.removeAfterWorldChange(notice.instanceId(), notice.playerId());
                    }
                }
            }
            case WIPED -> {
                lifecycle.info(notice.instanceId()).ifPresent(snapshot -> snapshot.players().stream()
                        .filter(value -> value.state() == PlayerLifecycleService.PlayerState.GHOST)
                        .map(value -> getServer().getPlayer(value.playerId()))
                        .filter(java.util.Objects::nonNull)
                        .forEach(BukkitGhostState::exit));
                RunPreparationService.RunSnapshot run = runPreparation.info(notice.instanceId()).orElse(null);
                boolean allParticipantsOffline = allParticipantsOffline(notice.instanceId());
                if (run != null && (run.state() == RunPreparationService.RunState.RUNNING
                        || run.state() == RunPreparationService.RunState.BOSS)) {
                    if (runPreparation.fail(notice.instanceId(), notice.detail()).successful()) {
                        if (allParticipantsOffline && phaseFiveCommand != null) {
                            phaseFiveCommand.wipeFromLifecycleAfterAllDisconnects(
                                    notice.instanceId(), notice.detail());
                        }
                        return;
                    }
                }
                if (run != null && run.state() == RunPreparationService.RunState.FAILED) {
                    if (allParticipantsOffline && phaseFiveCommand != null) {
                        phaseFiveCommand.wipeFromLifecycleAfterAllDisconnects(
                                notice.instanceId(), notice.detail());
                    }
                    return;
                }
                if (phaseFiveCommand != null) {
                    phaseFiveCommand.wipeFromLifecycle(notice.instanceId(), notice.detail());
                }
            }
            default -> { }
        }
    }

    static boolean shouldRestoreRemovedPlayer(Player player, String generationWorldName) {
        return player == null || generationWorldName.equals(player.getWorld().getName());
    }

    static boolean allParticipantsOffline(PlayerLifecycleService.InstanceSnapshot snapshot) {
        return !snapshot.players().isEmpty()
                && snapshot.players().stream().allMatch(player -> !player.online());
    }

    private boolean allParticipantsOffline(UUID instanceId) {
        return lifecycle.info(instanceId).map(DungeonCrawlers::allParticipantsOffline).orElse(false);
    }

    private Duration remainingGhostDuration(Instant reviveAt) {
        if (reviveAt == null) return Duration.ofMillis(50);
        Duration remaining = Duration.between(phaseClock().instant(), reviveAt);
        return remaining.isNegative() || remaining.isZero() ? Duration.ofMillis(50) : remaining;
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

    private boolean applyRevival(PlayerLifecycleService.Notice notice) {
        Player player = getServer().getPlayer(notice.playerId());
        if (player == null || !player.isOnline() || !player.getWorld().getName().equals(generationWorldName)) return false;
        if (notice.reviveTarget() != null) {
            Player target = getServer().getPlayer(notice.reviveTarget());
            if (target == null || !player.teleport(target.getLocation().clone().add(0, 1, 0))) return false;
        }
        healToFull(player);
        BukkitGhostState.exit(player);
        scheduleReviveHeal(notice.instanceId(), player, 1L);
        scheduleReviveHeal(notice.instanceId(), player, 20L);
        return true;
    }

    private void recordProgression(UUID instance, boolean success, UUID onlyPlayer) {
        var context = generation.layoutContext(instance).orElse(null);
        var run = runPreparation.info(instance).orElse(null);
        if (context == null || !context.progressionEnabled() || run == null || run.startedAt() == null) return;
        for (UUID participant : onlyPlayer == null ? run.participants() : List.of(onlyPlayer)) {
            if (onlyPlayer == null && lifecycle.player(instance, participant)
                    .map(value -> value.state() == PlayerLifecycleService.PlayerState.REMOVED).orElse(true)) continue;
            progression.record(instance, participant, context.floor(), context.difficulty(), success);
        }
    }

    private void deliverDungeonXp() {
        var nativeSkills = me.lidan.cavecrawlers.skills.SkillsManager.getInstance();
        var skill = nativeSkills.getSkillInfo("dungeon");
        if (skill == null) return;
        for (Player player : getServer().getOnlinePlayers()) progression.deliver(player.getUniqueId(),
                outcome -> nativeSkills.giveXpOnce(player, skill, outcome.xp(), outcome.id()),
                callback -> getServer().getScheduler().runTask(this, callback), getLogger()::warning);
    }

    private void startTasks() {
        getServer().getScheduler().runTaskTimer(this, (Runnable) () -> {
            centralUpdates.tick();
            foundryRooms.tick(phaseClock().instant());
            generation.checkCleanupDeadlines();
        }, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            combat.reconcileAll();
            if (placeholderExpansion != null) placeholderExpansion.refreshSnapshots();
            difficultyService.tick();
            classAbilities.tick();
            deliverDungeonXp();
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        disabling = true;
        if (progression != null && runPreparation != null) runPreparation.snapshots().forEach(
                run -> recordProgression(run.instanceId(), false, null));
        if (difficultyService != null) difficultyService.close();
        if (supportItems != null) supportItems.close();
        if (classAbilities != null) classAbilities.close();
        if (classSelectorNpcs != null) classSelectorNpcs.shutdown();
        if (placeholderExpansion != null) placeholderExpansion.unregister();
        if (reservations != null) reservations.pauseAdmission();
        if (generation != null) generation.freezeForDisable();
        if (runPreparation != null) runPreparation.freezeForDisable();
        if (centralUpdates != null) centralUpdates.freeze();
        if (lifecycle != null) lifecycle.freezeForDisable();
        getLogger().info("Shutdown: callbacks frozen and admission paused");

        if (progressBars != null) progressBars.cancelAll();
        if (classMenuService != null) classMenuService.closeAll();
        closeAllGuis();
        if (lifecycle != null) {
            lifecycle.instances().stream()
                    .flatMap(instance -> instance.players().stream())
                    .filter(value -> value.state() == PlayerLifecycleService.PlayerState.GHOST)
                    .map(value -> getServer().getPlayer(value.playerId()))
                    .filter(java.util.Objects::nonNull)
                    .forEach(BukkitGhostState::exit);
        }
        int restored = phaseFiveCommand == null ? 0 : phaseFiveCommand.restoreOnlinePlayersForDisable();
        getLogger().info("Shutdown: online snapshots restored=" + restored
                + "; offline recovery retained");

        if (phaseSeven != null) phaseSeven.cleanupAll();
        if (phaseNine != null) phaseNine.cleanupAll();
        if (combat != null) combat.cleanupAll();
        if (lifecycle != null) lifecycle.cleanupAll();
        if (runPreparation != null) runPreparation.cleanupAll();
        if (centralUpdates != null) centralUpdates.clear();
        getServer().getScheduler().cancelTasks(this);
        if (durableRepository != null) durableRepository.close();
        if (generationExecutor != null) generationExecutor.shutdownNow();
        latestScores.clear();
        getLogger().info("Shutdown complete; journals retained for startup recovery");
    }

    private void debugLog(String message) {
        if (debugSettings != null && debugSettings.enabled()) getLogger().info("[debug] " + message);
    }

    private void generationDiagnostic(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("failed") || normalized.contains("failure")
                || normalized.contains("deadline") || normalized.contains("blocked")
                || normalized.contains("callback") || normalized.startsWith("p0 ")) {
            diagnosticWarning(message);
        } else {
            debugLog(message);
        }
    }

    private void diagnosticWarning(String message) {
        getLogger().warning("DungeonCrawlers: " + message);
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

    private ClassSelectorNpcService createClassSelectorNpcService() {
        if (!getServer().getPluginManager().isPluginEnabled("Citizens")) {
            getLogger().info("Class Selector NPC: No (Citizens is unavailable; class menu remains enabled)");
            return new NoOpClassSelectorNpcService();
        }
        try {
            ClassSelectorNpcService service = new me.lidan.dungeonCrawlers.integration.citizens.CitizensClassSelectorNpcService(
                    this, classMenuService::open, configuredClassSelectorSkin());
            getLogger().info("Class Selector NPC: Yes");
            return service;
        } catch (Throwable failure) {
            getLogger().warning("Class Selector NPC: No (Citizens integration unavailable: "
                    + failure.getClass().getSimpleName() + ")");
            return new NoOpClassSelectorNpcService();
        }
    }

    private String configuredClassSelectorSkin() {
        String skin = mainConfig.getString("class-selector.npc.skin", "");
        return skin == null ? "" : skin.trim();
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
