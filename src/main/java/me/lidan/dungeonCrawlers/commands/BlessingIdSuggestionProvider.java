package me.lidan.dungeonCrawlers.commands;

import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.node.ExecutionContext;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

/** Instance-scoped sorted blessing ID suggestions. */
public final class BlessingIdSuggestionProvider<A extends CommandActor> implements SuggestionProvider<A> {
    private final Supplier<? extends Collection<String>> source;

    public BlessingIdSuggestionProvider(Supplier<? extends Collection<String>> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Collection<String> getSuggestions(ExecutionContext<A> context) {
        return source.get().stream().filter(Objects::nonNull).sorted().toList();
    }
}
