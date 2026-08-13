package me.lidan.dungeonCrawlers.integration.parties;

import me.lidan.dungeonCrawlers.integration.PartyProvider;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps Parties optional at class-load time while using its documented 3.2 API surface.
 * Every unexpected value or invocation failure returns ERROR; it never degrades to solo.
 */
public final class ReflectivePartiesAdapter implements PartyProvider {
    private final Server server;

    public ReflectivePartiesAdapter(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public PartyLookup lookup(UUID playerId) {
        Plugin plugin = server.getPluginManager().getPlugin("Parties");
        if (plugin == null) {
            return new PartyLookup(Status.SOLO, null, List.of(playerId), "Parties absent; positive solo fallback");
        }
        if (!plugin.isEnabled()) {
            return error("Parties is installed but disabled");
        }
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> partiesClass = Class.forName("com.alessiodp.parties.api.Parties", true, loader);
            Object api = partiesClass.getMethod("getApi").invoke(null);
            if (api == null) {
                return error("Parties.getApi() returned null");
            }
            Object partyPlayer = invoke(api, "getPartyPlayer", UUID.class, playerId);
            if (partyPlayer == null) {
                return error("Parties has no PartyPlayer record for online player");
            }
            boolean inParty = (boolean) partyPlayer.getClass().getMethod("isInParty").invoke(partyPlayer);
            if (!inParty) {
                return new PartyLookup(Status.SOLO, null, List.of(playerId), "Parties positively reports no party");
            }
            UUID partyId = (UUID) partyPlayer.getClass().getMethod("getPartyId").invoke(partyPlayer);
            Object party = invoke(api, "getParty", UUID.class, partyId);
            if (party == null) {
                return error("Parties returned no party for " + partyId);
            }
            UUID leader = (UUID) party.getClass().getMethod("getLeader").invoke(party);
            if (leader == null) {
                return error("Parties returned a party without a leader");
            }
            @SuppressWarnings("unchecked")
            Set<UUID> members = (Set<UUID>) party.getClass().getMethod("getMembers").invoke(party);
            List<UUID> online = new ArrayList<>();
            for (UUID memberId : members) {
                Player member = server.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    online.add(memberId);
                }
            }
            online.sort(UUID::compareTo);
            if (!online.contains(playerId)) {
                return error("requesting player is not in the party member snapshot");
            }
            int rank = (int) partyPlayer.getClass().getMethod("getRank").invoke(partyPlayer);
            return new PartyLookup(Status.PARTY, leader, online,
                    "party=" + partyId + ", rank=" + rank + ", online=" + online.size() + "/" + members.size());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return error(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static Object invoke(Object target, String method, Class<?> parameterType, Object argument)
            throws ReflectiveOperationException {
        Method candidate = target.getClass().getMethod(method, parameterType);
        return candidate.invoke(target, argument);
    }

    private static PartyLookup error(String detail) {
        return new PartyLookup(Status.ERROR, null, List.of(), detail);
    }
}
