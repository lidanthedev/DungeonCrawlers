package me.lidan.dungeonCrawlers.integration.parties;

import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.Party;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import me.lidan.dungeonCrawlers.integration.PartyProvider;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartiesAdapterTest {
    @Test
    void apiReportedSoloIsAccepted() {
        Server server = mock(Server.class);
        PartiesAPI api = mock(PartiesAPI.class);
        PartyPlayer partyPlayer = mock(PartyPlayer.class);
        UUID playerId = UUID.randomUUID();
        when(api.getPartyPlayer(playerId)).thenReturn(partyPlayer);
        when(partyPlayer.isInParty()).thenReturn(false);

        PartyProvider.PartyLookup result = new PartiesAdapter(server, api).lookup(playerId);

        assertEquals(PartyProvider.Status.SOLO, result.status());
        assertEquals(List.of(playerId), result.onlineMembers());
    }

    @Test
    void partySnapshotUsesTypedApiAndOnlineMembers() {
        Server server = mock(Server.class);
        PartiesAPI api = mock(PartiesAPI.class);
        PartyPlayer partyPlayer = mock(PartyPlayer.class);
        Party party = mock(Party.class);
        Player onlinePlayer = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        UUID offlineId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();

        when(api.getPartyPlayer(playerId)).thenReturn(partyPlayer);
        when(partyPlayer.isInParty()).thenReturn(true);
        when(partyPlayer.getPartyId()).thenReturn(partyId);
        when(partyPlayer.getRank()).thenReturn(2);
        when(api.getParty(partyId)).thenReturn(party);
        when(party.getLeader()).thenReturn(leaderId);
        when(party.getMembers()).thenReturn(Set.of(playerId, offlineId));
        when(server.getPlayer(playerId)).thenReturn(onlinePlayer);
        when(onlinePlayer.isOnline()).thenReturn(true);

        PartyProvider.PartyLookup result = new PartiesAdapter(server, api).lookup(playerId);

        assertEquals(PartyProvider.Status.PARTY, result.status());
        assertEquals(leaderId, result.leader());
        assertEquals(List.of(playerId), result.onlineMembers());
    }
}
