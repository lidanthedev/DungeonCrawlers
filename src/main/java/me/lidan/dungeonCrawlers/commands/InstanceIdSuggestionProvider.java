package me.lidan.dungeonCrawlers.commands;

import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.node.ExecutionContext;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Supplies currently active generation instance IDs to Lamp command completion. */
public final class InstanceIdSuggestionProvider implements SuggestionProvider<CommandActor> {
    private static volatile Supplier<? extends Collection<String>> source = List::of;

    public static void setSource(Supplier<? extends Collection<String>> source) {
        InstanceIdSuggestionProvider.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Collection<String> getSuggestions(ExecutionContext<CommandActor> context) {
        return source.get().stream().sorted().toList();
    }
}
