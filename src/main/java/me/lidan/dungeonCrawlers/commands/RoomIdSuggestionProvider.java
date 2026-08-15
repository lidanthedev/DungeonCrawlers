package me.lidan.dungeonCrawlers.commands;

import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.node.ExecutionContext;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

/** Supplies room IDs from the active configuration to Lamp command completion. */
public final class RoomIdSuggestionProvider<A extends CommandActor> implements SuggestionProvider<A> {
    private final Supplier<? extends Collection<String>> source;

    public RoomIdSuggestionProvider(Supplier<? extends Collection<String>> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Collection<String> getSuggestions(ExecutionContext<A> context) {
        return source.get().stream().sorted().toList();
    }
}
