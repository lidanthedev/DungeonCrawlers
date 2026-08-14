package me.lidan.dungeonCrawlers.integration.parties;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.Party;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import me.lidan.dungeonCrawlers.integration.PartyProvider;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Uses the supported Parties API after {@link PartyProviders} verifies the plugin is enabled. */
public final class PartiesAdapter implements PartyProvider {
    private final Server server;
    private final PartiesAPI api;

    PartiesAdapter(Server server) {
        this(server, Parties.getApi());
    }

    PartiesAdapter(Server server, PartiesAPI api) {
        this.server = Objects.requireNonNull(server, "server");
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public PartyLookup lookup(UUID playerId) {
        try {
            PartyPlayer partyPlayer = api.getPartyPlayer(playerId);
            if (partyPlayer == null) {
                return error("Parties has no PartyPlayer record for online player");
            }
            if (!partyPlayer.isInParty()) {
                return new PartyLookup(Status.SOLO, null, List.of(playerId),
                        "Parties positively reports no party");
            }

            UUID partyId = partyPlayer.getPartyId();
            if (partyId == null) {
                return error("Parties reports membership without a party id");
            }
            Party party = api.getParty(partyId);
            if (party == null) {
                return error("Parties returned no party for " + partyId);
            }
            UUID leader = party.getLeader();
            if (leader == null) {
                return error("Parties returned a party without a leader");
            }

            Set<UUID> members = party.getMembers();
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
            return new PartyLookup(Status.PARTY, leader, online,
                    "party=" + partyId + ", rank=" + partyPlayer.getRank()
                            + ", online=" + online.size() + "/" + members.size());
        } catch (LinkageError | RuntimeException exception) {
            return error(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static PartyLookup error(String detail) {
        return new PartyLookup(Status.ERROR, null, List.of(), detail);
    }
}
