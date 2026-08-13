package me.lidan.dungeonCrawlers.compatibility;

import me.lidan.cavecrawlers.stats.ActionBarManager;
import me.lidan.cavecrawlers.stats.StatsCalculateEvent;
import me.lidan.cavecrawlers.utils.Range;
import me.lidan.dungeonCrawlers.config.BootstrapSchemas;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CompatibilityService {
    public static final double V1_HEALTH_BALANCE_MAX = 2048;
    public static final double V1_DEFENSE_MAX = 1_000_000;
    public static final double V1_STRENGTH_MAX = 1_000_000;
    public static final double V1_INTELLIGENCE_MAX = 1_000_000;
    public static final double V1_CRIT_DAMAGE_MAX = 1_000_000;
    public static final double V1_CRIT_CHANCE_MAX = 100;
    public static final double V1_SPEED_MAX = 500;
    public static final double V1_ATTACK_SPEED_MAX = 100;
    public static final String[] FIXED_CONNECTOR_MATERIALS = {"AIR", "CAVE_AIR", "VOID_AIR"};

    private final JavaPlugin plugin;

    public CompatibilityService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompatibilityReport inspect() {
        List<ProbeResult> results = new ArrayList<>();
        requiredPlugin(results, "CaveCrawlers");
        requiredPlugin(results, "FastAsyncWorldEdit");
        requiredPlugin(results, "MythicMobs");
        requiredPlugin(results, "Vault");
        requiredPlugin(results, "ProtocolLib");
        optionalPlugin(results, "Parties");
        optionalPlugin(results, "Essentials");

        Range range = new Range(-2, 3);
        results.add(new ProbeResult("cavecrawlers.range", range.getMin() == -2 && range.getMax() == 3
                ? ProbeStatus.PASS : ProbeStatus.FAIL, "bounds=" + range.getMin() + ".." + range.getMax()
                + "; deterministic code must not call getRandom()"));
        results.add(new ProbeResult("cavecrawlers.actionbar", ActionBarManager.ACTION_BAR_COOLDOWN == 1000
                ? ProbeStatus.PASS : ProbeStatus.FAIL, "cooldown-ms=" + ActionBarManager.ACTION_BAR_COOLDOWN));
        results.add(new ProbeResult("stats.listeners", ProbeStatus.PASS, listenerInventory()));
        results.add(new ProbeResult("stats.caps", ProbeStatus.MANUAL_REQUIRED,
                "healthBalanceMax=" + V1_HEALTH_BALANCE_MAX + "; verify Paper MAX_HEALTH consumer with compatibility stats"));
        results.add(new ProbeResult("connectors.materials", ProbeStatus.PASS,
                "fixed=" + Arrays.toString(FIXED_CONNECTOR_MATERIALS)));

        List<String> schemaErrors = BootstrapSchemas.validate(
                new File(plugin.getDataFolder(), "classes.yml"),
                new File(plugin.getDataFolder(), "blessings.yml"));
        results.add(new ProbeResult("schemas.built-in", schemaErrors.isEmpty() ? ProbeStatus.PASS : ProbeStatus.FAIL,
                schemaErrors.isEmpty() ? "classes.yml and blessings.yml schema-version=1" : String.join("; ", schemaErrors)));
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

    private void optionalPlugin(List<ProbeResult> results, String name) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
        results.add(new ProbeResult("plugin." + name.toLowerCase(), dependency == null ? ProbeStatus.ABSENT : ProbeStatus.PASS,
                dependency == null ? "optional; solo/fallback path active" : dependency.getPluginMeta().getVersion()));
    }

    private String listenerInventory() {
        RegisteredListener[] listeners = StatsCalculateEvent.getHandlerList().getRegisteredListeners();
        if (listeners.length == 0) {
            return "no StatsCalculateEvent listeners registered";
        }
        return Arrays.stream(listeners)
                .map(listener -> listener.getPlugin().getName() + "@" + listener.getPriority())
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
    }
}
