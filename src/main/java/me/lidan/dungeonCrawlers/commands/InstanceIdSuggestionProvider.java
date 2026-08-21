package me.lidan.dungeonCrawlers.commands;

import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.node.ExecutionContext;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Supplies currently active generation instance IDs to Lamp command completion. */
public final class InstanceIdSuggestionProvider<A extends CommandActor> implements SuggestionProvider<A> {
    private final Supplier<? extends Collection<String>> source;

    public InstanceIdSuggestionProvider(Supplier<? extends Collection<String>> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Collection<String> getSuggestions(ExecutionContext<A> context) {
        return Stream.concat(Stream.of("this"), source.get().stream()).sorted().toList();
    }
}
