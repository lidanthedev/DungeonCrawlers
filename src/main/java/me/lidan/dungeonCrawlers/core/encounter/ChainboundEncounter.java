package me.lidan.dungeonCrawlers.core.encounter;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One boss, one health pool, and one irreversible phase progression per instance. */
public final class ChainboundEncounter implements EncounterFactory.Encounter {
    public static final String ID = "chainbound";
    public enum Stage { NEW, INTRO, FIRST, TRANSFORM, RIVEN, FINAL, DYING, COMPLETE, FAILED, CLEANED }
    public enum Attack { FORGE_WAVE, BRANDS, LANCE, CHAIN_DRAW, GUILLOTINE, RIFT_PULSE, LAST_WEAVE }

    public record Settings(double health, double damage, double transformThreshold, double finalThreshold,
                           long introMillis, long transformMillis, long warningMillis,
                           long normalIntervalMillis, long rivenIntervalMillis, long finalIntervalMillis,
                           long deathMillis) {
        public Settings {
            if (!Double.isFinite(health) || health <= 0 || !Double.isFinite(damage) || damage <= 0
                    || !Double.isFinite(transformThreshold) || !Double.isFinite(finalThreshold)
                    || transformThreshold >= 1 || transformThreshold <= finalThreshold || finalThreshold <= 0
                    || introMillis < 1000 || introMillis > 30000 || transformMillis < 8000 || transformMillis > 30000
                    || warningMillis < 1000 || warningMillis > 5000 || deathMillis < 1000 || deathMillis > 15000
                    || normalIntervalMillis < warningMillis + 3500 || normalIntervalMillis > 60000
                    || rivenIntervalMillis < warningMillis + 3500 || rivenIntervalMillis > normalIntervalMillis
                    || finalIntervalMillis < warningMillis + 3500 || finalIntervalMillis > rivenIntervalMillis) {
                throw new IllegalArgumentException("invalid chainbound encounter settings");
            }
        }
        public static Settings defaults() {
            return new Settings(200_000_000, 3_500_000, .65, .18, 6500, 14000, 2000, 8500, 6500, 5500, 5000);
        }
    }

    public interface Arena {
        void begin(UUID boss, Settings settings);
        void stage(Stage stage, Instant now);
        void tick(Instant now);
        double healthFraction();
        void cast(Attack attack, Instant now);
        void cleanup();
    }

    private final EncounterFactory.EncounterContext context;
    private final Arena arena;
    private final Settings settings;
    private final boolean impossible;
    private final Clock clock;
    private Stage stage = Stage.NEW;
    private UUID entity;
    private Instant deadline, nextAttack;
    private int pattern;
    private String failure;

    public ChainboundEncounter(EncounterFactory.EncounterContext context, Arena arena, Settings settings,
                               boolean impossible, Clock clock) {
        this.context = Objects.requireNonNull(context);
        this.arena = Objects.requireNonNull(arena);
        this.settings = Objects.requireNonNull(settings);
        this.impossible = impossible;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override public synchronized EncounterFactory.StartResult start() {
        if (stage != Stage.NEW) return EncounterFactory.StartResult.failure("chainbound is already started or cleaned");
        try {
            var spawned = context.entities().spawn(context.instanceId(), context.bossMob(), context.bossSpawn());
            if (!spawned.successful()) return failStart(spawned.detail());
            entity = spawned.entityId();
            arena.begin(entity, settings);
            enter(Stage.INTRO, clock.instant());
            return EncounterFactory.StartResult.success("Veyra has entered the foundry");
        } catch (RuntimeException exception) { return failStart(exception.toString()); }
    }

    @Override public synchronized EncounterFactory.TickResult tick(Instant now) {
        Objects.requireNonNull(now);
        if (stage == Stage.COMPLETE) return EncounterFactory.TickResult.complete("the chainbound architect is defeated");
        if (stage == Stage.FAILED) return EncounterFactory.TickResult.failure(failure);
        if (stage == Stage.NEW || stage == Stage.CLEANED) return EncounterFactory.TickResult.failure("chainbound is inactive");
        try {
            if (stage != Stage.DYING && (entity == null || !context.entities().isValid(entity)))
                return fail("chainbound boss disappeared");
            arena.tick(now);
            if (stage == Stage.CLEANED) return EncounterFactory.TickResult.failure("chainbound was cleaned during the update");
            if (stage == Stage.DYING) {
                if (!now.isBefore(deadline)) { arena.cleanup(); stage = Stage.COMPLETE; }
                return stage == Stage.COMPLETE ? EncounterFactory.TickResult.complete("the chains fall silent")
                        : EncounterFactory.TickResult.running("the architect's last chain is breaking");
            }
            if (stage == Stage.INTRO || stage == Stage.TRANSFORM) {
                if (now.isBefore(deadline)) return EncounterFactory.TickResult.running(stage.name());
                enter(stage == Stage.INTRO ? Stage.FIRST : Stage.RIVEN, now);
            }
            double health = arena.healthFraction();
            if (impossible && stage == Stage.FIRST && health <= settings.transformThreshold()) {
                enter(Stage.TRANSFORM, now);
                return EncounterFactory.TickResult.running("the cathedral is being torn apart");
            }
            if ((stage == Stage.FIRST || stage == Stage.RIVEN) && health <= settings.finalThreshold()) enter(Stage.FINAL, now);
            if (!now.isBefore(nextAttack)) {
                Attack attack = stage == Stage.FIRST || !impossible ? switch (pattern++ % 3) {
                    case 0 -> Attack.FORGE_WAVE;
                    case 1 -> Attack.BRANDS;
                    default -> Attack.LANCE;
                } : switch (pattern++ % 4) {
                    case 0 -> Attack.CHAIN_DRAW;
                    case 1 -> Attack.GUILLOTINE;
                    case 2 -> Attack.RIFT_PULSE;
                    default -> stage == Stage.FINAL ? Attack.LAST_WEAVE : Attack.BRANDS;
                };
                arena.cast(attack, now);
                nextAttack = now.plusMillis(stage == Stage.FINAL ? settings.finalIntervalMillis()
                        : stage == Stage.RIVEN ? settings.rivenIntervalMillis() : settings.normalIntervalMillis());
            }
            return EncounterFactory.TickResult.running("Veyra " + stage.name());
        } catch (RuntimeException exception) { return fail(exception.toString()); }
    }

    private void enter(Stage next, Instant now) {
        stage = next;
        pattern = 0;
        context.diagnostics().accept("chainbound stage=" + next);
        arena.stage(next, now);
        deadline = now.plusMillis(switch (next) {
            case INTRO -> settings.introMillis();
            case TRANSFORM -> settings.transformMillis();
            case DYING -> settings.deathMillis();
            default -> 0;
        });
        nextAttack = now.plusMillis(settings.warningMillis());
    }

    @Override public synchronized EncounterFactory.DeathResult onDeath(UUID killed) {
        Objects.requireNonNull(killed);
        if (!killed.equals(entity) || stage == Stage.DYING || stage == Stage.COMPLETE
                || stage == Stage.CLEANED || stage == Stage.FAILED || stage == Stage.NEW)
            return EncounterFactory.DeathResult.ignored("not the active chainbound architect");
        entity = null;
        try {
            enter(Stage.DYING, clock.instant());
            return EncounterFactory.DeathResult.accepted(false, "architect defeated; victory sequence pending");
        } catch (RuntimeException exception) {
            fail(exception.toString());
            return EncounterFactory.DeathResult.accepted(false, failure);
        }
    }

    private EncounterFactory.StartResult failStart(String detail) {
        fail(detail);
        return EncounterFactory.StartResult.failure(failure);
    }
    private EncounterFactory.TickResult fail(String detail) {
        failure = "chainbound encounter failed: " + detail;
        stage = Stage.FAILED;
        try { if (entity != null) context.entities().remove(entity); }
        finally { entity = null; arena.cleanup(); }
        return EncounterFactory.TickResult.failure(failure);
    }
    @Override public synchronized void cleanup() {
        if (stage == Stage.CLEANED) return;
        try { if (entity != null) context.entities().remove(entity); }
        finally {
            entity = null;
            arena.cleanup();
            if (stage != Stage.COMPLETE) stage = Stage.CLEANED;
        }
    }
    @Override public synchronized Optional<UUID> entityId() { return Optional.ofNullable(entity); }
    @Override public synchronized boolean complete() { return stage == Stage.COMPLETE; }
    public synchronized Stage stage() { return stage; }
}
