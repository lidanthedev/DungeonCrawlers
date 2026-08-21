package me.lidan.dungeonCrawlers.commands;

import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.node.ExecutionContext;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Supplier;

/** Supplies known Bukkit player names for admin OfflinePlayer arguments. */
public final class OfflinePlayerSuggestionProvider<A extends CommandActor> implements SuggestionProvider<A> {
    private final Supplier<? extends Collection<String>> source;

    public OfflinePlayerSuggestionProvider(Supplier<? extends Collection<String>> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Collection<String> getSuggestions(ExecutionContext<A> context) {
        return source.get().stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())).toList();
    }
}
