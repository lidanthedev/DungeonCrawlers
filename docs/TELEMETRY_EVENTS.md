# Completed addon events

Public immutable, non-cancellable events live in me.lidan.dungeonCrawlers.events. Consumers should use soft dependencies, check API availability, and compile without shading this addon.

DungeonRunStateChangedEvent carries a neutral RunPreparationService.StateChange snapshot after an actual transition. Repeated phase requests do not publish duplicate transitions. Cleanup uses closed=true. getRunLayout(UUID) exposes available floor/difficulty context without reflective access to private services. Run snapshots copy participants and selected classes.

DungeonRewardDeliveredEvent follows durable verified delivery. It carries the run, player, offer, whole-claim price and exact item quantities. Failed, capacity-blocked or pending verification paths do not publish a successful delivery. Repeated delivery of the same committed offer does not publish twice. Observation is best effort and is not a durable telemetry outbox.

DungeonCurrencyTransactionEvent follows a successful positive finite Vault gateway response. It includes actual provider amount and direction. It is not an affordability check. It also observes explicitly invoked administrative economy probes; consumers should filter their controlled accounts. The callback may be async and consumers must respect Bukkit thread rules.

Java 21 build checks transitions, durable delivery success/retry/blocking, successful payment observations, all existing tests and external API shading. Graphify update was attempted but the executable and graph are unavailable in this environment.
