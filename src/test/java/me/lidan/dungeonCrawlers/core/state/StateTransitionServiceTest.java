package me.lidan.dungeonCrawlers.core.state;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateTransitionServiceTest {
    private final StateTransitionService service = new StateTransitionService();

    @Test
    void acceptsOnlyLegalEdgesAndRepeatedState() {
        Set<String> legal = Set.of(
                "GENERATING:PREPARING", "GENERATING:DESTROYED",
                "PREPARING:RUNNING", "PREPARING:DESTROYED",
                "RUNNING:BOSS", "RUNNING:FAILED", "RUNNING:DESTROYED",
                "BOSS:COMPLETION_PENDING", "BOSS:FAILED", "BOSS:DESTROYED",
                "COMPLETION_PENDING:COMPLETED", "COMPLETED:DESTROYED", "FAILED:DESTROYED");
        for (InstanceState from : InstanceState.values()) {
            for (InstanceState to : InstanceState.values()) {
                var result = service.transition(from, to);
                boolean expected = from == to || legal.contains(from + ":" + to);
                if (expected) assertTrue(result.accepted(), from + " -> " + to);
                else assertFalse(result.accepted(), from + " -> " + to);
            }
        }
    }

    @Test
    void completionPendingCanOnlyBeDestroyedByStartupRecovery() {
        assertFalse(service.transition(InstanceState.COMPLETION_PENDING, InstanceState.DESTROYED).accepted());
        assertTrue(service.transition(InstanceState.COMPLETION_PENDING, InstanceState.DESTROYED, true).accepted());
    }
}
