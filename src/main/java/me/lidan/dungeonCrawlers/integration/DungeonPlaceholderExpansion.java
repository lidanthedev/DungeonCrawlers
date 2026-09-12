package me.lidan.dungeonCrawlers.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Optional PlaceholderAPI bridge backed only by the live in-memory snapshots. */
public final class DungeonPlaceholderExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final RunPreparationService runs;
    private final GenerationService generation;
    private final PlayerLifecycleService lifecycle;
    private final SecretDiscoveryService secrets;
    private final DebugSettings debug;
    private final Function<UUID, Integer> scoreLookup;

    public DungeonPlaceholderExpansion(JavaPlugin plugin, GenerationService generation,
                                       RunPreparationService runs,
                                       PlayerLifecycleService lifecycle, SecretDiscoveryService secrets,
                                       DebugSettings debug, Function<UUID, Integer> scoreLookup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.debug = Objects.requireNonNull(debug, "debug");
        this.scoreLookup = Objects.requireNonNull(scoreLookup, "scoreLookup");
    }

    @Override
    public String getIdentifier() {
        return "dungeoncrawlers";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isBlank()) return "";
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("version")) return getVersion();
        if (key.equals("debug")) return Boolean.toString(debug.enabled());
        if (key.equals("active_instances")) {
            return Long.toString(runs.snapshots().stream()
                    .filter(snapshot -> snapshot.state() != RunPreparationService.RunState.FAILED)
                    .count());
        }
        if (player == null) return "";
        if (key.equals("player_name")) return player.getName() == null ? "" : player.getName();
        if (key.equals("player_uuid")) return player.getUniqueId().toString();
        Context context = findContext(player.getUniqueId());
        if (context == null) {
            return switch (key) {
                case "in_dungeon" -> "false";
                case "ghost_seconds", "players", "alive", "ghosts", "secrets_found", "secrets_total" -> "0";
                default -> "";
            };
        }
        return value(key, context);
    }

    private Context findContext(UUID playerId) {
        try {
            for (RunPreparationService.RunSnapshot run : runs.snapshots()) {
                if (!run.participants().contains(playerId)) continue;
                PlayerLifecycleService.InstanceSnapshot group = lifecycle.info(run.instanceId()).orElse(null);
                if (group == null) return new Context(run, null, null, 0, 0);
                PlayerLifecycleService.PlayerSnapshot participant = group.players().stream()
                        .filter(value -> value.playerId().equals(playerId)).findFirst().orElse(null);
                SecretDiscoveryService.InstanceSnapshot secret = secrets.info(run.instanceId()).orElse(null);
                long alive = group.players().stream()
                        .filter(value -> value.state() == PlayerLifecycleService.PlayerState.ALIVE).count();
                long ghosts = group.players().stream()
                        .filter(value -> value.state() == PlayerLifecycleService.PlayerState.GHOST).count();
                return new Context(run, participant, secret, alive, ghosts);
            }
        } catch (RuntimeException ignored) {
            // Placeholder resolution must never affect gameplay when a provider is reloading.
        }
        return null;
    }

    private String value(String key, Context context) {
        RunPreparationService.RunSnapshot run = context.run();
        PlayerLifecycleService.PlayerSnapshot player = context.player();
        SecretDiscoveryService.InstanceSnapshot secret = context.secrets();
        return switch (key) {
            case "in_dungeon" -> "true";
            case "instance_id" -> run.instanceId().toString();
            case "instance_state" -> run.state().name().toLowerCase(Locale.ROOT);
            case "floor" -> floor(run.instanceId());
            case "score" -> score(run.instanceId());
            case "player_state" -> player == null ? "" : player.state().name().toLowerCase(Locale.ROOT);
            case "deaths" -> player == null ? "0" : Integer.toString(player.deaths());
            case "ghost_seconds" -> ghostSeconds(player);
            case "players" -> Integer.toString(run.participants().size());
            case "alive" -> Long.toString(context.alive());
            case "ghosts" -> Long.toString(context.ghosts());
            case "secrets_found" -> secret == null ? "0" : Long.toString(secret.secrets().stream()
                    .filter(SecretDiscoveryService.SecretSnapshot::discovered).count());
            case "secrets_total" -> secret == null ? "0" : Integer.toString(secret.secrets().size());
            default -> "";
        };
    }

    private String floor(UUID instanceId) {
        try {
            return generation.layoutContext(instanceId).map(value -> value.floor().id()).orElse("");
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String score(UUID instanceId) {
        Integer score = scoreLookup.apply(instanceId);
        return score == null ? "" : Integer.toString(score);
    }

    private static String ghostSeconds(PlayerLifecycleService.PlayerSnapshot player) {
        if (player == null || player.state() != PlayerLifecycleService.PlayerState.GHOST
                || player.reviveAt() == null) return "0";
        Duration remaining = Duration.between(Instant.now(), player.reviveAt());
        if (remaining.isNegative() || remaining.isZero()) return "0";
        return Long.toString(remaining.toSeconds() + (remaining.getNano() == 0 ? 0 : 1));
    }

    private record Context(RunPreparationService.RunSnapshot run,
                           PlayerLifecycleService.PlayerSnapshot player,
                           SecretDiscoveryService.InstanceSnapshot secrets,
                           long alive, long ghosts) { }
}
