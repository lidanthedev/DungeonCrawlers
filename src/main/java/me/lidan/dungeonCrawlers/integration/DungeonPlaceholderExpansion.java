package me.lidan.dungeonCrawlers.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.score.ScoreService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Optional PlaceholderAPI bridge backed only by live, immutable snapshots. */
public final class DungeonPlaceholderExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final RunPreparationService runs;
    private final GenerationService generation;
    private final PlayerLifecycleService lifecycle;
    private final SecretDiscoveryService secrets;
    private final CombatRoomService combat;
    private final DebugSettings debug;
    private final Function<UUID, Integer> legacyScoreLookup;
    private final Function<UUID, ScoreService.FinalScoreSnapshot> scoreLookup;

    /** Compatibility constructor for callers that only have a total score lookup. */
    public DungeonPlaceholderExpansion(JavaPlugin plugin, GenerationService generation,
                                       RunPreparationService runs,
                                       PlayerLifecycleService lifecycle, SecretDiscoveryService secrets,
                                       DebugSettings debug, Function<UUID, Integer> scoreLookup) {
        this(plugin, generation, runs, lifecycle, secrets, null, debug, scoreLookup, ignored -> null);
    }

    public DungeonPlaceholderExpansion(JavaPlugin plugin, GenerationService generation,
                                       RunPreparationService runs,
                                       PlayerLifecycleService lifecycle, SecretDiscoveryService secrets,
                                       CombatRoomService combat, DebugSettings debug,
                                       Function<UUID, ScoreService.FinalScoreSnapshot> scoreLookup) {
        this(plugin, generation, runs, lifecycle, secrets, combat, debug, ignored -> null, scoreLookup);
    }

    private DungeonPlaceholderExpansion(JavaPlugin plugin, GenerationService generation,
                                        RunPreparationService runs,
                                        PlayerLifecycleService lifecycle, SecretDiscoveryService secrets,
                                        CombatRoomService combat, DebugSettings debug,
                                        Function<UUID, Integer> legacyScoreLookup,
                                        Function<UUID, ScoreService.FinalScoreSnapshot> scoreLookup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.combat = combat;
        this.debug = Objects.requireNonNull(debug, "debug");
        this.legacyScoreLookup = Objects.requireNonNull(legacyScoreLookup, "legacyScoreLookup");
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
        try {
            return resolve(player, key);
        } catch (RuntimeException ignored) {
            // Scoreboards and tab lists must survive a reload or an instance disappearing mid-read.
            return fallback(key);
        }
    }

    private String resolve(OfflinePlayer player, String key) {
        switch (key) {
            case "version" -> { return getVersion(); }
            case "debug" -> { return Boolean.toString(debug.enabled()); }
            case "active_instances" -> { return Long.toString(activeRuns().stream()
                    .filter(run -> run.state() != RunPreparationService.RunState.FAILED).count()
                    + generatingCount()); }
            case "running_instances" -> { return Long.toString(activeRuns().stream()
                    .filter(run -> run.state() == RunPreparationService.RunState.RUNNING
                            || run.state() == RunPreparationService.RunState.BOSS).count()); }
            case "completed_instances" -> { return Long.toString(activeRuns().stream()
                    .filter(run -> run.state() == RunPreparationService.RunState.COMPLETION_PENDING
                            || run.state() == RunPreparationService.RunState.COMPLETED).count()); }
            case "generating_instances" -> { return Long.toString(generatingCount()); }
            case "active_players" -> { return Long.toString(activeRuns().stream()
                    .filter(run -> run.state() != RunPreparationService.RunState.FAILED)
                    .mapToLong(run -> run.participants().size()).sum()); }
            default -> { }
        }
        if (player == null) return "";
        if (key.equals("player_name")) return safePlayerName(player);
        if (key.equals("player_uuid")) return player.getUniqueId() == null ? "" : player.getUniqueId().toString();
        if (key.startsWith("instance_")) return instanceValue(key);

        Context context = findPlayerContext(player.getUniqueId());
        return context == null ? playerFallback(key) : playerValue(key, context);
    }

    private List<RunPreparationService.RunSnapshot> activeRuns() {
        return runs.snapshots();
    }

    private long generatingCount() {
        if (!Bukkit.isPrimaryThread()) return 0;
        try {
            return generation.instances().stream()
                    .filter(instance -> switch (instance.status()) {
                        case PLANNING, JOURNALING, PASTING -> true;
                        default -> false;
                    }).count();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private Context findPlayerContext(UUID playerId) {
        if (playerId == null) return null;
        try {
            UUID instanceId = runs.instanceFor(playerId).orElse(null);
            if (instanceId == null) return null;
            RunPreparationService.RunSnapshot run = runs.info(instanceId).orElse(null);
            return run == null ? null : context(run, playerId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Context findInstanceContext(UUID instanceId) {
        if (instanceId == null) return null;
        try {
            RunPreparationService.RunSnapshot run = runs.info(instanceId).orElse(null);
            return run == null ? null : context(run, null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Context context(RunPreparationService.RunSnapshot run, UUID playerId) {
        PlayerLifecycleService.InstanceSnapshot lifecycleSnapshot = lifecycle.info(run.instanceId()).orElse(null);
        PlayerLifecycleService.PlayerSnapshot participant = lifecycleSnapshot == null || playerId == null ? null
                : lifecycleSnapshot.players().stream().filter(value -> value.playerId().equals(playerId)).findFirst()
                .orElse(null);
        SecretDiscoveryService.InstanceSnapshot secretSnapshot = secrets.info(run.instanceId()).orElse(null);
        GenerationService.InstanceSnapshot generationSnapshot = safeGenerationInfo(run.instanceId());
        GenerationService.LayoutContext layout = safeLayoutContext(run.instanceId());
        CombatRoomService.InstanceSnapshot combatSnapshot = combat == null ? null
                : combat.info(run.instanceId()).orElse(null);
        ScoreService.FinalScoreSnapshot score = safeScore(run.instanceId());
        Integer legacyScore = safeLegacyScore(run.instanceId());
        return new Context(run, lifecycleSnapshot, participant, secretSnapshot, generationSnapshot, layout,
                combatSnapshot, score, legacyScore);
    }

    private GenerationService.InstanceSnapshot safeGenerationInfo(UUID instanceId) {
        if (!Bukkit.isPrimaryThread()) return null;
        try {
            return generation.info(instanceId).orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private GenerationService.LayoutContext safeLayoutContext(UUID instanceId) {
        if (!Bukkit.isPrimaryThread()) return null;
        try {
            return generation.layoutContext(instanceId).orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ScoreService.FinalScoreSnapshot safeScore(UUID instanceId) {
        try {
            return scoreLookup.apply(instanceId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Integer safeLegacyScore(UUID instanceId) {
        try {
            return legacyScoreLookup.apply(instanceId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String instanceValue(String key) {
        int fieldStart = key.indexOf('_', "instance_".length());
        if (fieldStart < 0) return "";
        UUID instanceId;
        try {
            instanceId = UUID.fromString(key.substring("instance_".length(), fieldStart));
        } catch (IllegalArgumentException exception) {
            return "false";
        }
        String field = key.substring(fieldStart + 1);
        if (field.equals("exists")) return Boolean.toString(findInstanceContext(instanceId) != null);
        Context context = findInstanceContext(instanceId);
        return context == null ? instanceFallback(field) : instanceValue(field, context);
    }

    private String instanceValue(String field, Context context) {
        RunPreparationService.RunSnapshot run = context.run();
        PlayerLifecycleService.InstanceSnapshot lifecycleSnapshot = context.lifecycle();
        return switch (field) {
            case "id" -> run.instanceId().toString();
            case "state" -> lower(run.state());
            case "floor", "floor_id" -> floorId(context);
            case "floor_name" -> floorName(context);
            case "seed" -> context.generation() == null ? "0" : Long.toString(context.generation().seed());
            case "players", "current_players" -> Integer.toString(run.participants().size());
            case "alive" -> Long.toString(countPlayers(lifecycleSnapshot, PlayerLifecycleService.PlayerState.ALIVE));
            case "ghosts" -> Long.toString(countPlayers(lifecycleSnapshot, PlayerLifecycleService.PlayerState.GHOST));
            case "deaths" -> Integer.toString(totalDeaths(lifecycleSnapshot));
            case "score" -> scoreTotal(context);
            case "rank" -> scoreRank(context);
            case "skill" -> scorePart(context, ScorePart.SKILL);
            case "time" -> scorePart(context, ScorePart.TIME);
            case "exploration" -> scorePart(context, ScorePart.EXPLORATION);
            case "bonus" -> scorePart(context, ScorePart.BONUS);
            case "elapsed_seconds" -> Long.toString(elapsedSeconds(context));
            case "elapsed_formatted" -> formatDuration(elapsedSeconds(context));
            case "rooms" -> context.combat() == null ? "0" : Integer.toString(context.combat().rooms().size());
            case "current_room" -> currentRoom(context, false);
            case "current_room_id" -> currentRoom(context, true);
            case "boss" -> boss(context);
            case "boss_encounter" -> context.layout() == null ? "" : safe(context.layout().floor().encounterId());
            case "reward_seconds_remaining" -> Long.toString(secondsRemaining(run.completionDeadline()));
            case "timeout_seconds_remaining" -> Long.toString(secondsRemaining(timeout(run)));
            default -> "";
        };
    }

    private String playerValue(String key, Context context) {
        PlayerLifecycleService.PlayerSnapshot player = context.player();
        return switch (key) {
            case "in_dungeon", "player_in_dungeon" -> "true";
            case "instance", "instance_id", "player_instance", "player_instance_id" ->
                    context.run().instanceId().toString();
            case "floor", "floor_id", "player_floor", "player_floor_id" -> floorId(context);
            case "player_floor_name" -> floorName(context);
            case "instance_state" -> lower(context.run().state());
            case "player_state" -> player == null ? "" : lower(player.state());
            case "alive" -> Long.toString(countPlayers(context.lifecycle(), PlayerLifecycleService.PlayerState.ALIVE));
            case "ghosts" -> Long.toString(countPlayers(context.lifecycle(), PlayerLifecycleService.PlayerState.GHOST));
            case "players" -> Integer.toString(context.run().participants().size());
            case "player_alive" -> Boolean.toString(player != null
                    && player.state() == PlayerLifecycleService.PlayerState.ALIVE);
            case "player_ghost" -> Boolean.toString(player != null
                    && player.state() == PlayerLifecycleService.PlayerState.GHOST);
            case "ghost_seconds", "player_respawn_seconds" -> Long.toString(ghostSeconds(player));
            case "deaths", "player_deaths" -> player == null ? "0" : Integer.toString(player.deaths());
            case "secrets", "player_secrets" -> secretCount(context, true) + "/" + secretCount(context, false);
            case "secrets_found", "player_secrets_found" -> Integer.toString(secretCount(context, true));
            case "secrets_total", "player_secrets_total" -> Integer.toString(secretCount(context, false));
            case "score", "player_score" -> scoreTotal(context);
            case "rank", "player_rank" -> scoreRank(context);
            case "player_skill_score" -> scorePart(context, ScorePart.SKILL);
            case "player_time_score" -> scorePart(context, ScorePart.TIME);
            case "player_exploration_score" -> scorePart(context, ScorePart.EXPLORATION);
            case "player_bonus_score" -> scorePart(context, ScorePart.BONUS);
            case "player_elapsed_time" -> formatDuration(elapsedSeconds(context));
            case "player_elapsed_seconds" -> Long.toString(elapsedSeconds(context));
            case "current_room", "player_current_room" -> currentRoom(context, false);
            case "current_room_id", "player_current_room_id" -> currentRoom(context, true);
            case "player_current_room_secrets", "player_current_room_secrets_found",
                 "player_current_room_secrets_total" -> "0";
            default -> "";
        };
    }

    private String floorId(Context context) {
        return context.layout() == null ? "" : safe(context.layout().floor().id());
    }

    private String floorName(Context context) {
        return context.layout() == null ? "" : safe(context.layout().floor().displayName());
    }

    private String boss(Context context) {
        return context.layout() == null ? "" : safe(context.layout().floor().bossMob());
    }

    private static long countPlayers(PlayerLifecycleService.InstanceSnapshot snapshot,
                                     PlayerLifecycleService.PlayerState state) {
        if (snapshot == null) return 0;
        return snapshot.players().stream().filter(player -> player.state() == state).count();
    }

    private static int totalDeaths(PlayerLifecycleService.InstanceSnapshot snapshot) {
        if (snapshot == null) return 0;
        return snapshot.players().stream().mapToInt(PlayerLifecycleService.PlayerSnapshot::deaths).sum();
    }

    private static int secretCount(Context context, boolean found) {
        if (context.secrets() == null) return 0;
        return (int) context.secrets().secrets().stream()
                .filter(secret -> !found || secret.discovered()).count();
    }

    private String scoreTotal(Context context) {
        if (context.score() != null) return Integer.toString(context.score().total());
        return context.legacyScore() == null ? "" : Integer.toString(context.legacyScore());
    }

    private String scoreRank(Context context) {
        if (context.score() == null) return "";
        return context.score().rank() == me.lidan.dungeonCrawlers.core.score.DungeonRank.S_PLUS
                ? "S+" : context.score().rank().name();
    }

    private static String scorePart(Context context, ScorePart part) {
        if (context.score() == null) return "";
        return Integer.toString(switch (part) {
            case SKILL -> context.score().skill();
            case TIME -> context.score().time();
            case EXPLORATION -> context.score().exploration();
            case BONUS -> context.score().bonus();
        });
    }

    private String currentRoom(Context context, boolean id) {
        if (context.combat() == null) return id ? "" : "0";
        return context.combat().rooms().stream()
                .filter(room -> room.state() == CombatRoomService.RoomState.ACTIVE)
                .findFirst()
                .map(room -> id ? safe(room.templateId()) : Integer.toString(room.index()))
                .orElse(id ? "" : "0");
    }

    private static long elapsedSeconds(Context context) {
        if (context.score() != null && (context.run().state() == RunPreparationService.RunState.COMPLETED
                || context.run().state() == RunPreparationService.RunState.FAILED)) {
            return Math.max(0, context.score().elapsed().toSeconds());
        }
        Instant started = context.run().startedAt();
        return started == null ? 0 : Math.max(0, Duration.between(started, Instant.now()).toSeconds());
    }

    private static Instant timeout(RunPreparationService.RunSnapshot run) {
        return switch (run.state()) {
            case PREPARING -> run.preparationDeadline();
            case RUNNING, BOSS -> run.runDeadline();
            case COMPLETION_PENDING, COMPLETED -> run.completionDeadline();
            case FAILED -> run.failedDeadline();
        };
    }

    private static long secondsRemaining(Instant deadline) {
        if (deadline == null) return 0;
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) return 0;
        return remaining.toSeconds() + (remaining.getNano() == 0 ? 0 : 1);
    }

    private static long ghostSeconds(PlayerLifecycleService.PlayerSnapshot player) {
        if (player == null || player.state() != PlayerLifecycleService.PlayerState.GHOST) return 0;
        return secondsRemaining(player.reviveAt());
    }

    private static String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return minutes > 0 ? minutes + "m " + remainder + "s" : remainder + "s";
    }

    private static String lower(Enum<?> value) {
        return value == null ? "" : value.name().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safePlayerName(OfflinePlayer player) {
        String name = player.getName();
        return name == null ? "" : name;
    }

    private static String playerFallback(String key) {
        return switch (key) {
            case "in_dungeon", "player_in_dungeon", "player_alive", "player_ghost" -> "false";
            case "ghost_seconds", "player_respawn_seconds", "players", "alive", "ghosts", "deaths",
                 "player_deaths", "secrets_found", "player_secrets_found", "secrets_total",
                 "player_secrets_total", "player_current_room_secrets", "player_current_room_secrets_found",
                 "player_current_room_secrets_total", "player_elapsed_seconds" -> "0";
            case "secrets", "player_secrets" -> "0/0";
            case "current_room", "player_current_room" -> "0";
            default -> "";
        };
    }

    private static String instanceFallback(String field) {
        return switch (field) {
            case "exists" -> "false";
            case "players", "current_players", "alive", "ghosts", "deaths", "seed", "rooms",
                 "elapsed_seconds", "reward_seconds_remaining", "timeout_seconds_remaining" -> "0";
            default -> "";
        };
    }

    private static String fallback(String key) {
        return key.startsWith("instance_") ? instanceFallback("exists") : playerFallback(key);
    }

    private enum ScorePart { SKILL, TIME, EXPLORATION, BONUS }

    private record Context(RunPreparationService.RunSnapshot run,
                           PlayerLifecycleService.InstanceSnapshot lifecycle,
                           PlayerLifecycleService.PlayerSnapshot player,
                           SecretDiscoveryService.InstanceSnapshot secrets,
                           GenerationService.InstanceSnapshot generation,
                           GenerationService.LayoutContext layout,
                           CombatRoomService.InstanceSnapshot combat,
                           ScoreService.FinalScoreSnapshot score,
                           Integer legacyScore) { }
}
