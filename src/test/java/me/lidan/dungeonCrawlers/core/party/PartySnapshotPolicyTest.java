package me.lidan.dungeonCrawlers.core.party;

import me.lidan.dungeonCrawlers.integration.PartyProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartySnapshotPolicyTest {
    private final PartySnapshotPolicy policy = new PartySnapshotPolicy();

    @Test
    void providerErrorAndOfflineLeaderFailClosed() {
        UUID requester = UUID.randomUUID();
        assertFalse(policy.snapshot(requester,
                new PartyProvider.PartyLookup(PartyProvider.Status.ERROR, null, List.of(), "timeout"), 5).successful());
        assertFalse(policy.snapshot(requester,
                new PartyProvider.PartyLookup(PartyProvider.Status.PARTY, UUID.randomUUID(), List.of(requester), "party"),
                5).successful());
    }

    @Test
    void leaderGetsImmutableOnlineSnapshot() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        var result = policy.snapshot(leader,
                new PartyProvider.PartyLookup(PartyProvider.Status.PARTY, leader, List.of(member, leader), "party"), 5);
        assertTrue(result.successful());
        assertTrue(result.snapshot().onlineMembers().containsAll(List.of(leader, member)));
    }
}
