package me.lidan.dungeonCrawlers.integration;

import java.util.List;
import java.util.UUID;

public interface PartyProvider {
    PartyLookup lookup(UUID playerId);

    enum Status { SOLO, PARTY, ERROR }

    record PartyLookup(Status status, UUID leader, List<UUID> onlineMembers, String detail) {
        public PartyLookup {
            onlineMembers = List.copyOf(onlineMembers);
        }
    }
}

