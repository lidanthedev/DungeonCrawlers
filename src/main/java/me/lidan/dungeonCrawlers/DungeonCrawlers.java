package me.lidan.dungeonCrawlers;

import dev.triumphteam.gui.guis.BaseGui;
import me.lidan.dungeonCrawlers.commands.DungeonCrawlersCommand;
import me.lidan.dungeonCrawlers.compatibility.CompatibilityService;
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

    @Override
    public void onEnable() {
        // Plugin startup logic
        commandHandlerBuilder = BukkitLamp.builder(this);
        registerSerializer();

        saveDefaultResources();
        registerCommandResolvers();
        registerCommandCompletions();
        registerCommands();
        registerEvents();

        startTasks();
    }

    private void registerSerializer() {
        // Register custom serializers if needed
    }

    private void saveDefaultResources() {
        saveResource("classes.yml", false);
        saveResource("blessings.yml", false);
        saveDefaultConfig();
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
        commandHandler.register(new DungeonCrawlersCommand(this, new CompatibilityService(this)));
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
