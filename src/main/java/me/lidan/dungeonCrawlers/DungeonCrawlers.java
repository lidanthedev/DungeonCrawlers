package me.lidan.dungeonCrawlers;

import dev.triumphteam.gui.guis.BaseGui;
import me.lidan.dungeonCrawlers.commands.DungeonCrawlersCommand;
import me.lidan.dungeonCrawlers.compatibility.CompatibilityService;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.config.registry.EncounterRegistry;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.core.reservation.PlayerReservationService;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.FileDurableRepository;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.Lamp;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class DungeonCrawlers extends JavaPlugin {
    private Lamp.Builder<BukkitCommandActor> commandHandlerBuilder;
    private ConfigRegistryService configRegistry;
    private PlayerReservationService reservations;
    private DurableRepository durableRepository;
    private BoostedCustomConfig mainConfig;

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
            mainConfig = new BoostedConfigFactory().openMainConfig(
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
        int backupRetention = mainConfig.getInt("backups.retention-count",
                ConfigRegistryService.DEFAULT_BACKUP_RETENTION);
        configRegistry = new ConfigRegistryService(getDataFolder().toPath(), encounters, backupRetention);
        ConfigRegistryService.ReloadResult loaded = configRegistry.initialize();
        if (!loaded.swapped()) {
            throw new IllegalStateException("Invalid DungeonCrawlers configuration: " + loaded.errors());
        }
        loaded.warnings().forEach(getLogger()::warning);
        reservations = new PlayerReservationService();
        int queueCapacity = loaded.snapshot().floors().values().stream()
                .mapToInt(floor -> floor.limits().repositoryQueueCapacity()).max().orElse(1_000);
        durableRepository = new FileDurableRepository(getDataFolder().toPath().resolve("runtime"), queueCapacity,
                callback -> getServer().getScheduler().runTask(this, callback));
        getLogger().info("Loaded Phase 1 config hash " + loaded.snapshot().hash());
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
        Lamp<BukkitCommandActor> commandHandler = commandHandlerBuilder.build();
        commandHandler.register(new DungeonCrawlersCommand(this,
                new CompatibilityService(this, mainConfig, configRegistry), mainConfig, configRegistry,
                reservations, durableRepository));
    }

    private void registerEvents() {
        // Register event listeners
    }

    private void startTasks() {
        // Start any repeating or scheduled tasks if needed
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getServer().getScheduler().cancelTasks(this);
        if (durableRepository != null) durableRepository.close();
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
