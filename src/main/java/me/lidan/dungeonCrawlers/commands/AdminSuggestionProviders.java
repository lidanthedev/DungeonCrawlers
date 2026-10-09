package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import revxrsal.commands.Lamp;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.node.ExecutionContext;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Context-dependent suggestions read existing dungeon state without changing it. */
public final class AdminSuggestionProviders {
    private AdminSuggestionProviders() { }

    public static void register(Lamp.Builder<BukkitCommandActor> builder, CombatRoomService combat,
                                RunPreparationService runs, Supplier<? extends Collection<String>> items,
                                Supplier<? extends Collection<String>> mobs, Supplier<? extends Collection<UUID>> enemies,
                                Supplier<? extends Collection<UUID>> claims) {
        builder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
            if (annotation.value() == ItemIds.class) return new ItemIds(items);
            if (annotation.value() == MobIds.class) return new MobIds(mobs);
            if (annotation.value() == RoomIndexes.class) return new RoomIndexes(combat, runs);
            if (annotation.value() == MobEntities.class) return new MobEntities(combat, runs);
            if (annotation.value() == RunicEntities.class) return new RunicEntities(enemies);
            if (annotation.value() == ReconciliationClaims.class) return new ReconciliationClaims(claims);
            return null;
        });
    }

    public record ItemIds(Supplier<? extends Collection<String>> source)
            implements SuggestionProvider<BukkitCommandActor> {
        @Override public Collection<String> getSuggestions(ExecutionContext<BukkitCommandActor> context) {
            return source.get().stream().sorted().toList();
        }
    }

    public record MobIds(Supplier<? extends Collection<String>> source)
            implements SuggestionProvider<BukkitCommandActor> {
        @Override public Collection<String> getSuggestions(ExecutionContext<BukkitCommandActor> context) {
            return source.get().stream().sorted().toList();
        }
    }

    public record RoomIndexes(CombatRoomService combat, RunPreparationService runs)
            implements SuggestionProvider<BukkitCommandActor> {
        @Override public Collection<String> getSuggestions(ExecutionContext<BukkitCommandActor> context) {
            return rooms(context, combat, runs).stream().map(CombatRoomService.RoomSnapshot::index)
                    .sorted().map(String::valueOf).toList();
        }
    }

    public record MobEntities(CombatRoomService combat, RunPreparationService runs)
            implements SuggestionProvider<BukkitCommandActor> {
        @Override public Collection<String> getSuggestions(ExecutionContext<BukkitCommandActor> context) {
            Integer index = context.getResolvedArgumentOrNull(Integer.class);
            if (index == null) return List.of();
            return rooms(context, combat, runs).stream().filter(room -> room.index() == index)
                    .flatMap(room -> room.requiredMobs().stream())
                    .filter(mob -> mob.state() == CombatRoomService.MobState.ALIVE && mob.entityId() != null)
                    .map(mob -> mob.entityId().toString()).sorted().toList();
        }
    }

    public record RunicEntities(Supplier<? extends Collection<UUID>> source)
            implements SuggestionProvider<BukkitCommandActor> {
        @Override public Collection<String> getSuggestions(ExecutionContext<BukkitCommandActor> context) {
            return source.get().stream().map(UUID::toString).sorted().toList();
        }
    }

    public record ReconciliationClaims(Supplier<? extends Collection<UUID>> source)
            implements SuggestionProvider<BukkitCommandActor> {
        @Override public Collection<String> getSuggestions(ExecutionContext<BukkitCommandActor> context) {
            return source.get().stream().map(UUID::toString).sorted().toList();
        }
    }

    private static List<CombatRoomService.RoomSnapshot> rooms(ExecutionContext<BukkitCommandActor> context,
                                                            CombatRoomService combat, RunPreparationService runs) {
        // Lamp normalizes parameter names; the instance ID is the unique preceding String argument.
        String instanceId = context.getResolvedArgumentOrNull(String.class);
        if (instanceId == null) return List.of();
        try {
            return combat.rooms(DungeonInstanceResolver.require(context.actor().sender(), instanceId, runs));
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }
}
