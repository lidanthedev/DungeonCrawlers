package me.lidan.dungeonCrawlers.core.encounter;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Two lives share one completion; attacks and performers belong to this instance. */
public final class RingmasterEncounter implements EncounterFactory.Encounter {
    public static final String ID = "ringmaster";
    public static final String ENCORE_MOB = "MadRingmasterEncore";
    public enum Attack { BALLOONS, CARDS, JACK_IN_THE_BOX, CARD_RING, CAROUSEL, SPOTLIGHT, SCYTHES }
    private enum Stage { NEW, INTRO, FIRST, TRANSITION, SECOND, COMPLETE, FAILED, CLEANED }

    public interface Arena {
        void begin(UUID boss, int life);
        default void ready() { }
        default void transition(Instant now) { }
        void tick(Instant now);
        boolean busy();
        void cast(Attack attack, Instant now);
        void summonPerformers();
        double healthFraction();
        void clear();
        void notice(String message);
        void cleanup();
    }

    private final EncounterFactory.EncounterContext context;
    private final Arena arena;
    private final Clock clock;
    private Stage stage = Stage.NEW;
    private UUID entityId;
    private Instant deadline;
    private Instant nextAttack;
    private Instant nextSummon;
    private int pattern;
    private boolean finale;
    private String failure;

    public RingmasterEncounter(EncounterFactory.EncounterContext context, Arena arena, Clock clock) {
        this.context = Objects.requireNonNull(context);
        this.arena = Objects.requireNonNull(arena);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public synchronized EncounterFactory.StartResult start() {
        if (stage != Stage.NEW) return EncounterFactory.StartResult.failure("ringmaster already started or cleaned");
        var result = spawn(context.bossMob(), 1);
        if (!result.successful()) return result;
        stage = Stage.INTRO;
        deadline = clock.instant().plusSeconds(6);
        arena.notice("<gold><bold>WELCOME TO THE MIDNIGHT CARNIVAL!</bold></gold>");
        return result;
    }

    @Override
    public synchronized EncounterFactory.TickResult tick(Instant now) {
        try {
            return advance(now);
        } catch (RuntimeException exception) {
            fail("ringmaster attack failed: " + exception.getMessage());
            return EncounterFactory.TickResult.failure(failure);
        }
    }

    private EncounterFactory.TickResult advance(Instant now) {
        Objects.requireNonNull(now);
        if (stage == Stage.COMPLETE) return EncounterFactory.TickResult.complete("ringmaster defeated");
        if (stage == Stage.FAILED) return EncounterFactory.TickResult.failure(failure);
        if (stage == Stage.NEW || stage == Stage.CLEANED) return EncounterFactory.TickResult.failure("ringmaster is inactive");
        if (stage == Stage.TRANSITION) {
            arena.tick(now);
            if (!now.isBefore(deadline)) {
                var spawned = spawn(ENCORE_MOB, 2);
                if (!spawned.successful()) return EncounterFactory.TickResult.failure(spawned.detail());
                stage = Stage.SECOND;
                pattern = 0;
                nextAttack = now.plusSeconds(2);
                arena.notice("<light_purple><bold>The world revolves!</bold></light_purple>");
            }
            return EncounterFactory.TickResult.running("ringmaster encore preparing");
        }
        if (entityId == null || !context.entities().isValid(entityId)) {
            fail("ringmaster entity disappeared");
            return EncounterFactory.TickResult.failure(failure);
        }
        arena.tick(now);
        if (stage == Stage.INTRO) {
            if (now.isBefore(deadline)) return EncounterFactory.TickResult.running("ringmaster introduction");
            stage = Stage.FIRST;
            arena.ready();
            nextAttack = now;
            nextSummon = now;
        }
        if (stage == Stage.FIRST && !now.isBefore(nextSummon) && !arena.busy()) {
            arena.summonPerformers();
            nextSummon = now.plusSeconds(15);
        }
        if (!arena.busy() && !now.isBefore(nextAttack)) {
            Attack attack;
            if (stage == Stage.SECOND) {
                if (!finale && arena.healthFraction() <= .3) {
                    attack = Attack.SCYTHES;
                    finale = true;
                } else {
                    attack = switch (pattern++ % 5) {
                        case 0 -> Attack.CAROUSEL;
                        case 1 -> Attack.CARDS;
                        case 2 -> Attack.SPOTLIGHT;
                        case 3 -> Attack.CARD_RING;
                        default -> Attack.BALLOONS;
                    };
                }
            } else {
                attack = switch (pattern++ % 3) {
                    case 0 -> Attack.BALLOONS;
                    case 1 -> Attack.CARDS;
                    default -> Attack.JACK_IN_THE_BOX;
                };
            }
            arena.cast(attack, now);
            nextAttack = now.plusMillis(switch (attack) {
                case BALLOONS -> 7500;
                case CARDS -> 7500;
                case JACK_IN_THE_BOX -> 9500;
                case CARD_RING -> 10000;
                case CAROUSEL -> 14000;
                case SPOTLIGHT -> 15500;
                case SCYTHES -> 16500;
            });
        }
        return EncounterFactory.TickResult.running("ringmaster " + stage.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public synchronized EncounterFactory.DeathResult onDeath(UUID killed) {
        Objects.requireNonNull(killed);
        if (entityId == null || !entityId.equals(killed) || stage == Stage.COMPLETE || stage == Stage.CLEANED
                || stage == Stage.FAILED) return EncounterFactory.DeathResult.ignored("not the active ringmaster");
        entityId = null;
        arena.clear();
        if (stage != Stage.SECOND) {
            stage = Stage.TRANSITION;
            deadline = clock.instant().plusSeconds(6);
            arena.transition(clock.instant());
            arena.notice("<dark_purple><bold>YOU THOUGHT THE SHOW WAS OVER?</bold></dark_purple>");
            return EncounterFactory.DeathResult.accepted(false, "ringmaster fake death; encore pending");
        }
        stage = Stage.COMPLETE;
        arena.cleanup();
        return EncounterFactory.DeathResult.accepted(true, "ringmaster encore defeated");
    }

    private EncounterFactory.StartResult spawn(String mob, int life) {
        var result = context.entities().spawn(context.instanceId(), mob, context.bossSpawn());
        if (!result.successful()) {
            fail("ringmaster life " + life + " spawn failed: " + result.detail());
            return EncounterFactory.StartResult.failure(failure);
        }
        entityId = result.entityId();
        arena.begin(entityId, life);
        return EncounterFactory.StartResult.success("ringmaster life " + life + " spawned");
    }

    private void fail(String detail) {
        failure = detail;
        stage = Stage.FAILED;
        if (entityId != null && context.entities().isValid(entityId)) context.entities().remove(entityId);
        entityId = null;
        arena.cleanup();
    }

    @Override
    public synchronized void cleanup() {
        if (stage == Stage.CLEANED) return;
        if (entityId != null && context.entities().isValid(entityId)) context.entities().remove(entityId);
        entityId = null;
        arena.cleanup();
        if (stage != Stage.COMPLETE) stage = Stage.CLEANED;
    }

    @Override public synchronized Optional<UUID> entityId() { return Optional.ofNullable(entityId); }
    @Override public synchronized boolean complete() { return stage == Stage.COMPLETE; }
}
