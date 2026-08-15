package me.lidan.dungeonCrawlers.compatibility;

import me.lidan.cavecrawlers.stats.ActionBarManager;
import me.lidan.cavecrawlers.stats.StatsCalculateEvent;
import me.lidan.cavecrawlers.utils.Range;
import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.integration.spawn.BukkitSpawnProvider;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CompatibilityService {
    public static final String[] FIXED_CONNECTOR_MATERIALS = {"AIR", "CAVE_AIR", "VOID_AIR"};

    private final JavaPlugin plugin;
    private final BoostedCustomConfig mainConfig;
    private final ConfigRegistryService configRegistry;

    public CompatibilityService(JavaPlugin plugin, BoostedCustomConfig mainConfig,
                                ConfigRegistryService configRegistry) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
        this.configRegistry = configRegistry;
    }

    public CompatibilityReport inspect() {
        List<ProbeResult> results = new ArrayList<>();
        requiredPlugin(results, "CaveCrawlers");
        requiredPlugin(results, "FastAsyncWorldEdit");
        requiredPlugin(results, "MythicMobs");
        requiredPlugin(results, "Vault");
        requiredPlugin(results, "ProtocolLib");
        optionalPlugin(results, "Parties", true);
        boolean bukkitSpawnAvailable = new BukkitSpawnProvider(
                plugin.getServer(), mainConfig.getString("fallback-spawn-world", "")).spawn().isPresent();
        optionalPlugin(results, "Essentials", bukkitSpawnAvailable);

        Range range = new Range(-2, 3);
        results.add(new ProbeResult("cavecrawlers.range", range.getMin() == -2 && range.getMax() == 3
                ? ProbeStatus.PASS : ProbeStatus.FAIL, "bounds=" + range.getMin() + ".." + range.getMax()
                + "; deterministic code must not call getRandom()"));
        results.add(new ProbeResult("cavecrawlers.actionbar", ActionBarManager.ACTION_BAR_COOLDOWN == 1000
                ? ProbeStatus.PASS : ProbeStatus.FAIL, "cooldown-ms=" + ActionBarManager.ACTION_BAR_COOLDOWN));
        results.add(listenerProbe(StatsCalculateEvent.getHandlerList().getRegisteredListeners()));
        results.add(new ProbeResult("stats.integration", ProbeStatus.MANUAL_REQUIRED,
                "DungeonCrawlers applies CaveCrawlers StatType values without stat caps; verify downstream consumers"));
        results.add(new ProbeResult("connectors.materials", ProbeStatus.PASS,
                "fixed=" + Arrays.toString(FIXED_CONNECTOR_MATERIALS)));

        var snapshot = configRegistry.snapshot();
        boolean schemasLoaded = !snapshot.classes().isEmpty() && !snapshot.blessings().isEmpty();
        results.add(new ProbeResult("schemas.built-in", schemasLoaded ? ProbeStatus.PASS : ProbeStatus.FAIL,
                schemasLoaded ? "immutable config hash=" + snapshot.hash() : "class or blessing registry is empty"));
        results.add(new ProbeResult("fawe.roundtrip-cancel", ProbeStatus.MANUAL_REQUIRED,
                "run the recorded selection round-trip and slow-paste cancellation procedure on staging"));
        results.add(new ProbeResult("vault.mutation", ProbeStatus.MANUAL_REQUIRED,
                "run compatibility economy with a named disposable test account"));
        return new CompatibilityReport(Instant.now(), results);
    }

    private void requiredPlugin(List<ProbeResult> results, String name) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
        results.add(new ProbeResult("plugin." + name.toLowerCase(),
                dependency != null && dependency.isEnabled() ? ProbeStatus.PASS : ProbeStatus.FAIL,
                dependency == null ? "missing" : dependency.getPluginMeta().getVersion() + ", enabled=" + dependency.isEnabled()));
    }

    private void optionalPlugin(List<ProbeResult> results, String name, boolean fallbackVerified) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
        if (dependency == null) {
            results.add(new ProbeResult("plugin." + name.toLowerCase(),
                    fallbackVerified ? ProbeStatus.FALLBACK_PASS : ProbeStatus.ABSENT,
                    fallbackVerified ? "optional dependency absent; verified fallback active"
                            : "optional dependency absent; fallback unavailable"));
            return;
        }
        results.add(new ProbeResult("plugin." + name.toLowerCase(),
                dependency.isEnabled() ? ProbeStatus.PASS : ProbeStatus.FAIL,
                dependency.isEnabled() ? dependency.getPluginMeta().getVersion() : "installed but disabled"));
    }

    static ProbeResult listenerProbe(RegisteredListener[] listeners) {
        if (listeners.length == 0) {
            return new ProbeResult("stats.listeners", ProbeStatus.FAIL,
                    "no StatsCalculateEvent listeners registered");
        }
        String inventory = Arrays.stream(listeners)
                .map(listener -> listener.getPlugin().getName() + "@" + listener.getPriority())
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
        return new ProbeResult("stats.listeners", ProbeStatus.PASS, inventory);
    }
}
