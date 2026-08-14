package me.lidan.dungeonCrawlers.core.party;

import me.lidan.dungeonCrawlers.integration.PartyProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartySnapshotPolicyTest {
    private final PartySnapshotPolicy policy = new PartySnapshotPolicy();

    @Test
    void providerErrorAndNonLeaderFailClosed() {
        UUID requester = UUID.randomUUID();
        assertFalse(policy.snapshot(requester,
                new PartyProvider.PartyLookup(PartyProvider.Status.ERROR, null, List.of(), "timeout"), 5).successful());
        assertFalse(policy.snapshot(requester,
                new PartyProvider.PartyLookup(PartyProvider.Status.PARTY, UUID.randomUUID(), List.of(requester), "party"),
                5).successful());
    }

    @Test
    void malformedAndOversizedMembershipFailsClosed() {
        UUID requester = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        assertFalse(policy.snapshot(requester,
                new PartyProvider.PartyLookup(PartyProvider.Status.SOLO, requester, List.of(member), "solo"), 5)
                .successful());
        assertFalse(policy.snapshot(requester,
                new PartyProvider.PartyLookup(PartyProvider.Status.PARTY, requester,
                        List.of(requester, member, member), "party"), 5).successful());
        assertFalse(policy.snapshot(requester,
                new PartyProvider.PartyLookup(PartyProvider.Status.PARTY, requester, List.of(member), "party"), 5)
                .successful());
        assertFalse(policy.snapshot(requester,
                new PartyProvider.PartyLookup(PartyProvider.Status.PARTY, requester, List.of(requester, member), "party"), 1)
                .successful());
        assertFalse(policy.snapshot(requester,
                new PartyProvider.PartyLookup(PartyProvider.Status.PARTY, requester, List.of(requester), "party"), 0)
                .successful());
    }

    @Test
    void leaderGetsImmutableOnlineSnapshot() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        var result = policy.snapshot(leader,
                new PartyProvider.PartyLookup(PartyProvider.Status.PARTY, leader, List.of(member, leader), "party"), 5);
        assertTrue(result.successful());
        assertEquals(List.of(leader, member).stream().sorted().toList(), result.snapshot().onlineMembers());
        assertThrows(UnsupportedOperationException.class,
                () -> result.snapshot().onlineMembers().add(UUID.randomUUID()));
    }
}
