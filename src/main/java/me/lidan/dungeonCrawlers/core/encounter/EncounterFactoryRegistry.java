package me.lidan.dungeonCrawlers.core.encounter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Instance-safe encounter factory registry; factories are immutable after startup registration. */
public final class EncounterFactoryRegistry {
    private final Map<String, EncounterFactory> factories = new LinkedHashMap<>();

    public synchronized boolean register(String id, EncounterFactory factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        if (!id.matches("^[a-z0-9][a-z0-9_-]{0,63}$")) {
            throw new IllegalArgumentException("invalid encounter id: " + id);
        }
        if (factories.containsKey(id)) return false;
        factories.put(id, factory);
        return true;
    }

    public synchronized Optional<EncounterFactory> factory(String id) {
        return Optional.ofNullable(factories.get(Objects.requireNonNull(id, "id")));
    }

    public synchronized Set<String> ids() { return Set.copyOf(factories.keySet()); }

    public static EncounterFactoryRegistry withBasic() {
        EncounterFactoryRegistry registry = new EncounterFactoryRegistry();
        registry.register("basic", BasicBossEncounter::new);
        registry.register("multistage_test", MultistageTestEncounter::new);
        registry.register("factory_failure_test", ignored -> {
            throw new IllegalStateException("factory failure test");
        });
        return registry;
    }
}
