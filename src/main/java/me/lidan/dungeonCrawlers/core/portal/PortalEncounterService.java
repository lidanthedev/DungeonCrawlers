package me.lidan.dungeonCrawlers.core.portal;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.core.encounter.BossEntityGateway;
import me.lidan.dungeonCrawlers.core.encounter.EncounterFactory;
import me.lidan.dungeonCrawlers.core.encounter.EncounterFactoryRegistry;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.LayoutPlan;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.Placement;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Owns the portal countdown and the isolated boss encounter for a generated run. */
public final class PortalEncounterService {
    public static final Duration PORTAL_COUNTDOWN = Duration.ofSeconds(5);

    private final CentralUpdateService updates;
    private final RunPreparationService runs;
    private final EncounterFactoryRegistry factories;
    private final BossEntityGateway entities;
    private final ParticipantGateway participants;
    private final Clock clock;
    private final Consumer<String> diagnostics;
    private final Map<UUID, MutableInstance> instances = new LinkedHashMap<>();

    public PortalEncounterService(CentralUpdateService updates, RunPreparationService runs,
                                  EncounterFactoryRegistry factories, BossEntityGateway entities,
                                  ParticipantGateway participants, Clock clock,
                                  Consumer<String> diagnostics) {
        this.updates = Objects.requireNonNull(updates, "updates");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.factories = Objects.requireNonNull(factories, "factories");
        this.entities = Objects.requireNonNull(entities, "entities");
        this.participants = Objects.requireNonNull(participants, "participants");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public synchronized RegistrationResult register(UUID instanceId, FloorDefinition floor, LayoutPlan plan) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(plan, "plan");
        if (!instanceId.equals(plan.instanceId())) return RegistrationResult.failure("layout instance mismatch");
        if (instances.containsKey(instanceId)) return RegistrationResult.failure("portal state already registered");
        Placement portal = plan.placements().stream()
                .filter(value -> value.type() == me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType.PORTAL)
                .findFirst().orElse(null);
        Placement boss = plan.placements().stream()
                .filter(value -> value.type() == me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType.BOSS)
                .findFirst().orElse(null);
        if (portal == null) return RegistrationResult.failure("generated layout has no PORTAL placement");
        if (portal.portalBlocks().isEmpty()) return RegistrationResult.failure("PORTAL has no portal blocks");
        if (boss == null) return RegistrationResult.failure("generated layout has no BOSS placement");
        if (boss.bossSpawn().isEmpty()) return RegistrationResult.failure("BOSS has no RED boss spawn");
        if (boss.rewardChest().isEmpty()) return RegistrationResult.failure("BOSS has no LIME reward location");
        if (boss.playerSpawns().isEmpty()) return RegistrationResult.failure("BOSS has no EMERALD player spawn");
        Point bossSpawn = boss.bossSpawn().orElseThrow();
        Point reward = boss.rewardChest().orElseThrow();
        if (bossSpawn.equals(reward)) return RegistrationResult.failure("boss spawn and reward locations must differ");
        if (factories.factory(floor.encounterId()).isEmpty()) {
            return RegistrationResult.failure("unknown encounter factory: " + floor.encounterId());
        }
        MutableInstance state = new MutableInstance(instanceId, floor, Set.copyOf(portal.portalBlocks()),
                boss.playerSpawns(), bossSpawn, reward);
        state.callback = now -> tick(instanceId, now);
        instances.put(instanceId, state);
        return RegistrationResult.success("portal and boss state registered", snapshot(state));
    }

    public synchronized PortalResult enterPortal(UUID instanceId, UUID playerId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        Objects.requireNonNull(playerId, "playerId");
        if (state == null) return PortalResult.failure("unknown portal instance " + instanceId);
        if (state.status == Status.BOSS || state.status == Status.COMPLETION_PENDING) {
            return PortalResult.success("boss encounter already active", snapshot(state));
        }
        if (state.status == Status.FAILED) return PortalResult.failure(state.detail);
        if (runs.info(instanceId).map(value -> value.state() != RunPreparationService.RunState.RUNNING)
                .orElse(true)) return PortalResult.failure("run is not running");
        if (runs.info(instanceId).map(value -> !value.participants().contains(playerId)).orElse(true)) {
            return PortalResult.failure("player is not an active participant");
        }
        if (state.owner != null) {
            if (state.owner.equals(playerId)) return PortalResult.success("portal countdown already active", snapshot(state));
            return PortalResult.failure("portal countdown owned by " + state.owner);
        }
        if (!updates.registerSupplemental(instanceId, state.callback)) {
            return PortalResult.failure("central update is not registered for this instance");
        }
        state.owner = playerId;
        state.deadline = clock.instant().plus(PORTAL_COUNTDOWN);
        state.lastAnnouncedCountdown = (int) PORTAL_COUNTDOWN.toSeconds();
        state.status = Status.COUNTDOWN;
        state.detail = "portal countdown started by " + participants.displayName(playerId);
        announceCountdown(state, state.lastAnnouncedCountdown);
        return PortalResult.success(state.detail, snapshot(state));
    }

    /** Starts the countdown for an administrator using the first active participant as owner. */
    public synchronized PortalResult startPortal(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return PortalResult.failure("unknown portal instance " + instanceId);
        UUID owner = participants.activePlayers(instanceId).stream().findFirst().orElse(null);
        if (owner == null) return PortalResult.failure("no active participant can own the portal countdown");
        return enterPortal(instanceId, owner);
    }

    public synchronized PortalResult abortPortal(UUID instanceId, UUID playerId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        Objects.requireNonNull(playerId, "playerId");
        if (state == null) return PortalResult.failure("unknown portal instance " + instanceId);
        if (state.status != Status.COUNTDOWN) return PortalResult.success("portal countdown is not active", snapshot(state));
        if (!playerId.equals(state.owner)) return PortalResult.failure("only the countdown owner can abort the portal");
        return abortInternal(state, "portal countdown aborted by " + participants.displayName(playerId));
    }

    public synchronized PortalResult abortPortal(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return PortalResult.failure("unknown portal instance " + instanceId);
        if (state.status != Status.COUNTDOWN) return PortalResult.success("portal countdown is not active", snapshot(state));
        return abortInternal(state, "portal countdown aborted by administrator");
    }

    public synchronized PortalResult startBoss(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return PortalResult.failure("unknown portal instance " + instanceId);
        if (state.status == Status.BOSS) return PortalResult.success("boss encounter already active", snapshot(state));
        if (state.status == Status.COMPLETION_PENDING) return PortalResult.success("boss already defeated", snapshot(state));
        boolean registered = false;
        if (state.status == Status.COUNTDOWN) {
            state.owner = null;
            state.deadline = null;
        } else if (!updates.registerSupplemental(instanceId, state.callback)) {
            return PortalResult.failure("central update is not registered for this instance");
        } else {
            registered = true;
        }
        PortalResult result = startBossInternal(state);
        if (registered && !result.successful() && state.status != Status.FAILED) {
            updates.removeSupplemental(instanceId, state.callback);
        }
        return result;
    }

    public synchronized DeathResult onBossDeath(UUID instanceId, UUID entityId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        Objects.requireNonNull(entityId, "entityId");
        if (state == null || state.encounter == null) return DeathResult.ignored("boss encounter is not active");
        EncounterFactory.DeathResult death = state.encounter.onDeath(entityId);
        if (!death.accepted()) return DeathResult.ignored(death.detail());
        if (!death.completed()) return DeathResult.accepted(death.detail(), snapshot(state));
        return complete(state, death.detail());
    }

    public synchronized BossResult killBoss(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null || state.encounter == null) return BossResult.failure("boss encounter is not active");
        UUID entityId = state.encounter.entityId().orElse(null);
        if (entityId == null) return BossResult.failure("boss has no active entity");
        entities.remove(entityId);
        DeathResult result = onBossDeath(instanceId, entityId);
        return result.accepted() ? BossResult.success(result.detail(), snapshot(state))
                : BossResult.failure(result.detail());
    }

    public synchronized BossResult status(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        return state == null ? BossResult.failure("unknown portal instance " + instanceId)
                : BossResult.success(state.detail, snapshot(state));
    }

    public synchronized Optional<UUID> portalAt(Point point) {
        Objects.requireNonNull(point, "point");
        return instances.values().stream().filter(state -> state.portalBlocks.contains(point))
                .map(state -> state.instanceId).findFirst();
    }

    /** Returns the portal and countdown-owner indexes used by the Bukkit movement listener. */
    public synchronized PortalLocations portalLocations() {
        Map<Point, UUID> portals = new LinkedHashMap<>();
        Map<UUID, UUID> owners = new LinkedHashMap<>();
        instances.values().forEach(state -> {
            state.portalBlocks.forEach(point -> portals.put(point, state.instanceId));
            if (state.status == Status.COUNTDOWN && state.owner != null) owners.put(state.instanceId, state.owner);
        });
        return new PortalLocations(portals, owners);
    }

    public synchronized boolean isPortalBlock(UUID instanceId, Point point) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        return state != null && state.portalBlocks.contains(Objects.requireNonNull(point, "point"));
    }

    public synchronized boolean isCountdownOwner(UUID instanceId, UUID playerId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        return state != null && state.status == Status.COUNTDOWN && playerId.equals(state.owner);
    }

    /** Aborts a countdown when its owner steps out before the four-second deadline. */
    public synchronized PortalResult ownerLeftPortal(UUID instanceId, UUID playerId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return PortalResult.failure("unknown portal instance " + instanceId);
        return isCountdownOwner(instanceId, playerId) ? abortPortal(instanceId, playerId)
                : PortalResult.success("portal countdown is not owned by player", snapshot(state));
    }

    public synchronized Optional<Snapshot> info(UUID instanceId) {
        MutableInstance state = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        return state == null ? Optional.empty() : Optional.of(snapshot(state));
    }

    public synchronized boolean cleanup(UUID instanceId) {
        MutableInstance state = instances.remove(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return false;
        updates.removeSupplemental(instanceId, state.callback);
        if (state.encounter != null) state.encounter.cleanup();
        return true;
    }

    public synchronized void cleanupAll() { new ArrayList<>(instances.keySet()).forEach(this::cleanup); }

    private void tick(UUID instanceId, Instant now) {
        synchronized (this) {
            MutableInstance state = instances.get(instanceId);
            if (state == null) return;
            if (state.status == Status.COUNTDOWN && state.deadline != null && !now.isBefore(state.deadline)) {
                state.owner = null;
                state.deadline = null;
                PortalResult started = startBossInternal(state);
                if (!started.successful()) diagnostics.accept("instance=" + instanceId + " " + started.detail());
                return;
            }
            if (state.status == Status.COUNTDOWN && state.deadline != null) {
                int remainingSeconds = (int) Math.ceil(Duration.between(now, state.deadline).toMillis() / 1_000D);
                if (remainingSeconds > 0 && remainingSeconds < state.lastAnnouncedCountdown) {
                    state.lastAnnouncedCountdown = remainingSeconds;
                    announceCountdown(state, remainingSeconds);
                }
            }
            if (state.status != Status.BOSS || state.encounter == null) return;
            EncounterFactory.TickResult result = state.encounter.tick(now);
            if (!result.successful()) {
                state.status = Status.FAILED;
                state.detail = result.detail();
                runs.fail(instanceId, result.detail());
                updates.removeSupplemental(instanceId, state.callback);
                diagnostics.accept("instance=" + instanceId + " " + result.detail());
            } else if (result.completed()) {
                complete(state, result.detail());
            }
        }
    }

    private PortalResult startBossInternal(MutableInstance state) {
        if (runs.info(state.instanceId).map(value -> value.state() != RunPreparationService.RunState.RUNNING)
                .orElse(true)) return PortalResult.failure("run is not running");
        RunPreparationService.PhaseResult transition = runs.enterBoss(state.instanceId);
        if (!transition.successful()) return PortalResult.failure(transition.detail());
        Set<UUID> registered = runs.info(state.instanceId).map(value -> Set.copyOf(value.participants()))
                .orElse(Set.of());
        List<UUID> active = participants.activePlayers(state.instanceId).stream()
                .filter(registered::contains).sorted().toList();
        if (active.isEmpty()) return failStart(state, "no active participants for boss encounter");
        try {
            for (int index = 0; index < active.size(); index++) {
                Point target = state.playerSpawns.get(index % state.playerSpawns.size());
                if (!participants.teleport(active.get(index), target)) {
                    return failStart(state, "boss teleport failed for " + active.get(index));
                }
            }
            EncounterFactory factory = factories.factory(state.floor.encounterId()).orElse(null);
            if (factory == null) return failStart(state, "unknown encounter factory: " + state.floor.encounterId());
            state.encounter = factory.create(new EncounterFactory.EncounterContext(state.instanceId,
                    state.floor.encounterId(), state.floor.bossMob(), state.bossSpawn, entities,
                    message -> diagnostics.accept("instance=" + state.instanceId + " " + message)));
            if (state.encounter == null) return failStart(state, "encounter factory returned null");
            EncounterFactory.StartResult started = state.encounter.start();
            if (!started.successful()) return failStart(state, started.detail());
        } catch (RuntimeException exception) {
            return failStart(state, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        state.status = Status.BOSS;
        state.detail = "boss encounter active encounter=" + state.floor.encounterId();
        participants.notice(state.instanceId, "<red>Boss encounter started.</red>");
        return PortalResult.success(state.detail, snapshot(state));
    }

    private PortalResult failStart(MutableInstance state, String detail) {
        state.status = Status.FAILED;
        state.detail = detail;
        runs.fail(state.instanceId, detail);
        updates.removeSupplemental(state.instanceId, state.callback);
        if (state.encounter != null) state.encounter.cleanup();
        participants.notice(state.instanceId, "<red>Boss encounter failed: " + detail + "</red>");
        return PortalResult.failure(detail);
    }

    private DeathResult complete(MutableInstance state, String detail) {
        RunPreparationService.PhaseResult transition = runs.enterCompletionPending(state.instanceId);
        if (!transition.successful()) return DeathResult.ignored(transition.detail());
        state.status = Status.COMPLETION_PENDING;
        state.detail = detail + "; reward location=" + state.rewardChest;
        updates.removeSupplemental(state.instanceId, state.callback);
        participants.notice(state.instanceId, "<green>Boss defeated. Finalizing dungeon...</green>");
        return DeathResult.accepted(detail, snapshot(state));
    }

    private PortalResult abortInternal(MutableInstance state, String detail) {
        UUID owner = state.owner;
        state.owner = null;
        state.deadline = null;
        state.lastAnnouncedCountdown = 0;
        state.status = Status.IDLE;
        state.detail = detail;
        updates.removeSupplemental(state.instanceId, state.callback);
        if (owner != null) {
            participants.title(state.instanceId, "<red><bold>Boss Aborted</bold></red>",
                    "<yellow>by <white>" + participants.displayName(owner) + "</white></yellow>");
        }
        return PortalResult.success(detail, snapshot(state));
    }

    private void announceCountdown(MutableInstance state, int seconds) {
        participants.title(state.instanceId, "<red><bold>Boss Starting</bold></red>",
                "<yellow>In <white>" + seconds + "</white> seconds</yellow>");
    }

    private Snapshot snapshot(MutableInstance state) {
        long remaining = state.deadline == null ? 0 : Math.max(0,
                Duration.between(clock.instant(), state.deadline).toMillis());
        return new Snapshot(state.instanceId, state.status, state.owner, state.deadline,
                state.encounter == null ? null : state.encounter.entityId().orElse(null),
                state.bossSpawn, state.rewardChest, state.detail, remaining);
    }

    public interface ParticipantGateway {
        List<UUID> activePlayers(UUID instanceId);

        boolean teleport(UUID playerId, Point target);

        void notice(UUID instanceId, String miniMessage);

        default String displayName(UUID playerId) { return playerId.toString(); }

        /** Sends a MiniMessage title and subtitle to every participant in the run. */
        default void title(UUID instanceId, String miniMessageTitle, String miniMessageSubtitle) { }
    }

    public enum Status { IDLE, COUNTDOWN, BOSS, COMPLETION_PENDING, FAILED }

    public record RegistrationResult(boolean successful, String detail, Snapshot snapshot) {
        public RegistrationResult {
            Objects.requireNonNull(detail, "detail");
        }

        public static RegistrationResult success(String detail, Snapshot snapshot) {
            return new RegistrationResult(true, detail, snapshot);
        }
        public static RegistrationResult failure(String detail) { return new RegistrationResult(false, detail, null); }
    }

    public record PortalResult(boolean successful, String detail, Snapshot snapshot) {
        public PortalResult {
            Objects.requireNonNull(detail, "detail");
        }

        public static PortalResult success(String detail, Snapshot snapshot) {
            return new PortalResult(true, detail, snapshot);
        }
        public static PortalResult failure(String detail) { return new PortalResult(false, detail, null); }
    }

    public record DeathResult(boolean accepted, String detail, Snapshot snapshot) {
        public DeathResult {
            Objects.requireNonNull(detail, "detail");
        }

        public static DeathResult accepted(String detail, Snapshot snapshot) {
            return new DeathResult(true, detail, snapshot);
        }
        public static DeathResult ignored(String detail) { return new DeathResult(false, detail, null); }
    }

    public record BossResult(boolean successful, String detail, Snapshot snapshot) {
        public BossResult {
            Objects.requireNonNull(detail, "detail");
        }

        public static BossResult success(String detail, Snapshot snapshot) {
            return new BossResult(true, detail, snapshot);
        }
        public static BossResult failure(String detail) { return new BossResult(false, detail, null); }
    }

    public record Snapshot(UUID instanceId, Status status, UUID countdownOwner, Instant countdownDeadline,
                           UUID bossEntity, Point bossSpawn, Point rewardChest, String detail,
                           long countdownRemainingMillis) {
        public Snapshot {
            Objects.requireNonNull(instanceId); Objects.requireNonNull(status); Objects.requireNonNull(bossSpawn);
            Objects.requireNonNull(rewardChest); Objects.requireNonNull(detail);
        }
    }

    public record PortalLocations(Map<Point, UUID> portals, Map<UUID, UUID> countdownOwners) {
        public PortalLocations {
            portals = Map.copyOf(portals);
            countdownOwners = Map.copyOf(countdownOwners);
        }
    }

    private static final class MutableInstance {
        private final UUID instanceId;
        private final FloorDefinition floor;
        private final Set<Point> portalBlocks;
        private final List<Point> playerSpawns;
        private final Point bossSpawn;
        private final Point rewardChest;
        private java.util.function.Consumer<Instant> callback;
        private Status status = Status.IDLE;
        private UUID owner;
        private Instant deadline;
        private int lastAnnouncedCountdown;
        private EncounterFactory.Encounter encounter;
        private String detail = "portal ready";

        private MutableInstance(UUID instanceId, FloorDefinition floor, Set<Point> portalBlocks,
                                List<Point> playerSpawns, Point bossSpawn, Point rewardChest) {
            this.instanceId = instanceId;
            this.floor = floor;
            this.portalBlocks = Set.copyOf(portalBlocks);
            this.playerSpawns = List.copyOf(playerSpawns);
            this.bossSpawn = bossSpawn;
            this.rewardChest = rewardChest;
        }
    }
}
