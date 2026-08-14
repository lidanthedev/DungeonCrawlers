package me.lidan.dungeonCrawlers.core.state;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StateTransitionService {
    private static final Map<InstanceState, Set<InstanceState>> LEGAL = legalTransitions();

    public TransitionResult transition(InstanceState current, InstanceState target) {
        return transition(current, target, false);
    }

    public TransitionResult transition(InstanceState current, InstanceState target, boolean startupRecovery) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(target, "target");
        if (current == target) {
            return new TransitionResult(current, target, true, false, "already in " + target);
        }
        boolean allowed = LEGAL.getOrDefault(current, Set.of()).contains(target);
        if (current == InstanceState.COMPLETION_PENDING && target == InstanceState.DESTROYED) {
            allowed = startupRecovery;
        }
        return new TransitionResult(current, target, allowed, allowed,
                allowed ? "transition accepted" : "illegal transition");
    }

    private static Map<InstanceState, Set<InstanceState>> legalTransitions() {
        Map<InstanceState, Set<InstanceState>> result = new EnumMap<>(InstanceState.class);
        result.put(InstanceState.GENERATING, EnumSet.of(InstanceState.PREPARING, InstanceState.DESTROYED));
        result.put(InstanceState.PREPARING, EnumSet.of(InstanceState.RUNNING, InstanceState.DESTROYED));
        result.put(InstanceState.RUNNING, EnumSet.of(InstanceState.BOSS, InstanceState.FAILED, InstanceState.DESTROYED));
        result.put(InstanceState.BOSS, EnumSet.of(InstanceState.COMPLETION_PENDING, InstanceState.FAILED,
                InstanceState.DESTROYED));
        result.put(InstanceState.COMPLETION_PENDING, EnumSet.of(InstanceState.COMPLETED));
        result.put(InstanceState.COMPLETED, EnumSet.of(InstanceState.DESTROYED));
        result.put(InstanceState.FAILED, EnumSet.of(InstanceState.DESTROYED));
        return Map.copyOf(result);
    }

    public record TransitionResult(InstanceState from, InstanceState to, boolean accepted, boolean changed,
                                   String detail) {
    }
}
