package me.lidan.dungeonCrawlers.core.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityIdentityTest {
    @Test
    void validatesInstanceAndRoomIdentity() {
        UUID instance = UUID.randomUUID();
        assertEquals(instance, new EntityIdentity(instance, 2).instanceId());
        assertThrows(IllegalArgumentException.class, () -> new EntityIdentity(instance, -1));
    }
}
