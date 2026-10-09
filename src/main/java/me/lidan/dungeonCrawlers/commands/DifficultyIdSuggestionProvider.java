package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.difficulty.Difficulty;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.node.ExecutionContext;

import java.util.Arrays;
import java.util.Collection;

/** Supplies difficulty IDs in progression order. */
public final class DifficultyIdSuggestionProvider<A extends CommandActor> implements SuggestionProvider<A> {
    @Override
    public Collection<String> getSuggestions(ExecutionContext<A> context) {
        return Arrays.stream(Difficulty.values()).map(Difficulty::id).toList();
    }
}
