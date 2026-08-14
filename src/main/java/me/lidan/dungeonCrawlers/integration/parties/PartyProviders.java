package me.lidan.dungeonCrawlers.integration.parties;

import me.lidan.dungeonCrawlers.integration.PartyProvider;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

public final class PartyProviders {
    private PartyProviders() {
    }

    public static PartyProvider forServer(Server server) {
        Objects.requireNonNull(server, "server");
        Plugin plugin = server.getPluginManager().getPlugin("Parties");
        if (plugin == null) {
            return playerId -> new PartyProvider.PartyLookup(PartyProvider.Status.SOLO, null,
                    List.of(playerId), "Parties absent; positive solo fallback");
        }
        if (!plugin.isEnabled()) {
            return unavailable("Parties is installed but disabled");
        }
        try {
            return new PartiesAdapter(server);
        } catch (LinkageError | RuntimeException exception) {
            return unavailable(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static PartyProvider unavailable(String detail) {
        return playerId -> new PartyProvider.PartyLookup(PartyProvider.Status.ERROR, null, List.of(), detail);
    }
}
