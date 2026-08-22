package me.lidan.dungeonCrawlers.core.claim;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RewardDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RewardItem;
import me.lidan.dungeonCrawlers.core.reward.RewardEntitlementService;
import me.lidan.dungeonCrawlers.core.score.DungeonRank;
import me.lidan.dungeonCrawlers.core.score.ScoreService;
import me.lidan.dungeonCrawlers.integration.CaveItemsGateway;
import me.lidan.dungeonCrawlers.integration.EconomyGateway;
import me.lidan.dungeonCrawlers.persistence.DurableRecord;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.DurableSubmission;
import me.lidan.dungeonCrawlers.persistence.DurableWrite;
import me.lidan.dungeonCrawlers.persistence.DurableWriteReceipt;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardClaimServiceTest {
    private static final Instant COMPLETED = Instant.parse("2026-08-22T00:00:00Z");
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000081");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000082");

    @BeforeEach
    void setUpBukkit() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void successfulClaimCallsEconomyOnceAndClosesSiblingOffers() {
        RewardEntitlementService entitlements = entitlements();
        AtomicInteger withdrawals = new AtomicInteger();
        EconomyGateway economy = new EconomyGateway() {
            @Override
            public String providerIdentity() { return "TestEconomy"; }

            @Override
            public TransactionResult withdraw(OfflinePlayer player, double amount) {
                withdrawals.incrementAndGet();
                return new TransactionResult(true, amount, 10, "charged");
            }

            @Override
            public TransactionResult deposit(OfflinePlayer player, double amount) {
                return new TransactionResult(true, amount, 10, "credited");
            }
        };
        RewardClaimService claims = new RewardClaimService(clock(), entitlements, items(), economy);
        OfflinePlayer player = player();
        AtomicReference<RewardClaimService.ClaimResult> result = new AtomicReference<>();

        claims.claim(INSTANCE, PLAYER, "gold", player, result::set);

        assertTrue(result.get().successful(), result.get().detail());
        assertEquals(1, withdrawals.get());
        RewardClaimService.ClaimRecord record = claims.info(INSTANCE, PLAYER).orElseThrow();
        assertEquals(ClaimGroup.State.CLAIMED, record.claimGroup().state());
        assertEquals(OfferState.OWNED, record.offers().get(record.claimGroup().winnerOfferId()).state());
        assertTrue(record.offers().values().stream().filter(value -> !value.offerId()
                .equals(record.claimGroup().winnerOfferId())).allMatch(value -> value.state() == OfferState.EXPIRED));

        claims.claim(INSTANCE, PLAYER, "wood", player, result::set);
        assertEquals(RewardClaimService.ClaimStatus.ALREADY_CLAIMED, result.get().status());
        assertEquals(1, withdrawals.get());
    }

    @Test
    void ambiguousDebitIsReconciliationOnlyAndNotChargedReleasesTheGroup() {
        RewardEntitlementService entitlements = entitlements();
        AtomicInteger withdrawals = new AtomicInteger();
        EconomyGateway economy = new EconomyGateway() {
            @Override
            public String providerIdentity() { return "TestEconomy"; }

            @Override
            public TransactionResult withdraw(OfflinePlayer player, double amount) {
                withdrawals.incrementAndGet();
                throw new IllegalStateException("provider timeout");
            }

            @Override
            public TransactionResult deposit(OfflinePlayer player, double amount) {
                throw new AssertionError("refund must never be automatic");
            }
        };
        RewardClaimService claims = new RewardClaimService(clock(), entitlements, items(), economy);
        AtomicReference<RewardClaimService.ClaimResult> result = new AtomicReference<>();

        claims.claim(INSTANCE, PLAYER, "gold", player(), result::set);

        assertEquals(RewardClaimService.ClaimStatus.RECONCILIATION_REQUIRED, result.get().status());
        assertEquals(1, withdrawals.get());
        assertEquals(OfferState.RECONCILIATION_REQUIRED,
                claims.info(INSTANCE, PLAYER).orElseThrow().offers().values().stream()
                        .filter(value -> value.price() == 5).findFirst().orElseThrow().state());

        UUID offerId = claims.info(INSTANCE, PLAYER).orElseThrow().offers().values().stream()
                .filter(value -> value.price() == 5).findFirst().orElseThrow().offerId();
        AtomicReference<RewardClaimService.ReconcileResult> reconciliation = new AtomicReference<>();
        claims.reconcile(offerId, RewardClaimService.Decision.NOT_CHARGED, "test", "provider log says no debit",
                reconciliation::set);

        assertTrue(reconciliation.get().successful(), reconciliation.get().detail());
        assertEquals(ClaimGroup.State.NONE, claims.info(INSTANCE, PLAYER).orElseThrow().claimGroup().state());
        assertEquals(1, withdrawals.get());
    }

    @Test
    void freeClaimDoesNotRequireAnEconomyProvider() {
        RewardClaimService claims = new RewardClaimService(clock(), entitlements(), items(), null);
        AtomicReference<RewardClaimService.ClaimResult> result = new AtomicReference<>();

        claims.claim(INSTANCE, PLAYER, "wood", player(), result::set);

        assertTrue(result.get().successful(), result.get().detail());
        RewardClaimService.ClaimRecord record = claims.info(INSTANCE, PLAYER).orElseThrow();
        UUID winner = record.claimGroup().winnerOfferId();
        assertEquals(OfferState.OWNED, record.offers().get(winner).state());
    }

    @Test
    void deliveryClearsProvenanceAfterDurableDeliveryAck() {
        EconomyGateway economy = new EconomyGateway() {
            @Override
            public String providerIdentity() { return "TestEconomy"; }

            @Override
            public TransactionResult withdraw(OfflinePlayer player, double amount) {
                return new TransactionResult(true, amount, 10, "charged");
            }

            @Override
            public TransactionResult deposit(OfflinePlayer player, double amount) {
                return new TransactionResult(true, amount, 10, "credited");
            }
        };
        PlayerMock online = new PlayerMock(MockBukkit.getMock(), "Claimant", PLAYER);
        MockBukkit.getMock().addPlayer(online);
        RewardClaimService claims = new RewardClaimService(clock(), entitlements(), items(), economy);
        AtomicReference<RewardClaimService.ClaimResult> claim = new AtomicReference<>();
        claims.claim(INSTANCE, PLAYER, "gold", online, claim::set);
        UUID offerId = claim.get().offerId();
        AtomicReference<RewardClaimService.DeliveryResult> delivery = new AtomicReference<>();

        claims.deliver(INSTANCE, PLAYER, online, delivery::set);

        assertTrue(delivery.get().successful(), delivery.get().detail());
        assertTrue(java.util.Arrays.stream(online.getInventory().getContents())
                .anyMatch(item -> item != null && item.getType() == Material.GOLD_INGOT));
        ItemStack delivered = java.util.Arrays.stream(online.getInventory().getContents())
                .filter(java.util.Objects::nonNull)
                .filter(item -> item.getType() == Material.GOLD_INGOT)
                .findFirst().orElseThrow();
        assertFalse(RewardClaimService.isPending(delivered));
        assertFalse(RewardClaimService.claimId(delivered).isPresent());
        assertFalse(RewardClaimService.itemId(delivered).isPresent());
        assertFalse(RewardClaimService.ownerId(delivered).isPresent());
        assertEquals(OfferState.DELIVERED,
                claims.info(INSTANCE, PLAYER).orElseThrow().offers().get(offerId).state());
    }

    @Test
    void definiteDebitFailureReleasesTheClaimGroupForRetry() {
        AtomicInteger withdrawals = new AtomicInteger();
        EconomyGateway economy = new EconomyGateway() {
            @Override
            public String providerIdentity() { return "TestEconomy"; }

            @Override
            public TransactionResult withdraw(OfflinePlayer player, double amount) {
                withdrawals.incrementAndGet();
                return new TransactionResult(false, amount, 1, "insufficient funds");
            }

            @Override
            public TransactionResult deposit(OfflinePlayer player, double amount) {
                throw new AssertionError("definite failure must not refund");
            }
        };
        RewardClaimService claims = new RewardClaimService(clock(), entitlements(), items(), economy);
        AtomicReference<RewardClaimService.ClaimResult> result = new AtomicReference<>();

        claims.claim(INSTANCE, PLAYER, "gold", player(), result::set);

        assertEquals(RewardClaimService.ClaimStatus.REJECTED, result.get().status());
        RewardClaimService.ClaimRecord record = claims.info(INSTANCE, PLAYER).orElseThrow();
        UUID offerId = record.offers().values().stream().filter(value -> value.price() == 5)
                .findFirst().orElseThrow().offerId();
        assertEquals(OfferState.AVAILABLE, record.offers().get(offerId).state());
        assertEquals(ClaimGroup.State.NONE, record.claimGroup().state());

        claims.claim(INSTANCE, PLAYER, "gold", player(), result::set);
        assertEquals(2, withdrawals.get());
    }

    @Test
    void ownershipAckFailureLeavesDebitAttemptForReconciliationAndRestartRestoresCommittedOwnership() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.failRuntimeAckVersion = 3;
        RewardClaimService claims = new RewardClaimService(clock(), repository, entitlements(), items(),
                () -> successfulEconomy(), Runnable::run, ignored -> { });
        claims.ready().join();
        AtomicReference<RewardClaimService.ClaimResult> result = new AtomicReference<>();

        claims.claim(INSTANCE, PLAYER, "gold", player(), result::set);

        assertEquals(RewardClaimService.ClaimStatus.RECONCILIATION_REQUIRED, result.get().status());
        assertEquals(OfferState.DEBIT_ATTEMPTED, claims.info(INSTANCE, PLAYER).orElseThrow().offers().values()
                .stream().filter(value -> value.price() == 5).findFirst().orElseThrow().state());

        RewardClaimService restarted = new RewardClaimService(clock(), repository, entitlements(), items(),
                () -> successfulEconomy(), Runnable::run, ignored -> { });
        restarted.ready().join();
        assertEquals(OfferState.OWNED, restarted.info(INSTANCE, PLAYER).orElseThrow().offers().values()
                .stream().filter(value -> value.price() == 5).findFirst().orElseThrow().state());
    }

    @Test
    void fullInventoryRejectsBeforeDebitAndAllowsRetryAfterMakingRoom() {
        PlayerMock online = new PlayerMock(MockBukkit.getMock(), "Claimant", PLAYER);
        MockBukkit.getMock().addPlayer(online);
        ItemStack[] full = new ItemStack[online.getInventory().getSize()];
        java.util.Arrays.fill(full, new ItemStack(Material.STONE, 64));
        full[0] = new ItemStack(Material.GOLD_INGOT, 63);
        online.getInventory().setContents(full);
        AtomicInteger withdrawals = new AtomicInteger();
        EconomyGateway economy = new EconomyGateway() {
            @Override
            public String providerIdentity() { return "TestEconomy"; }

            @Override
            public TransactionResult withdraw(OfflinePlayer player, double amount) {
                withdrawals.incrementAndGet();
                return new TransactionResult(true, amount, 10, "charged");
            }

            @Override
            public TransactionResult deposit(OfflinePlayer player, double amount) {
                return new TransactionResult(true, amount, 10, "credited");
            }
        };
        RewardClaimService claims = new RewardClaimService(clock(), entitlements(), items(), economy);
        AtomicReference<RewardClaimService.ClaimResult> claim = new AtomicReference<>();
        claims.claim(INSTANCE, PLAYER, "gold", online, claim::set);

        assertEquals(RewardClaimService.ClaimStatus.REJECTED, claim.get().status());
        assertEquals("inventory is full; make room before purchasing", claim.get().detail());
        assertEquals(0, withdrawals.get());
        assertTrue(claims.info(INSTANCE, PLAYER).isEmpty());

        online.getInventory().setItem(0, null);
        claims.claim(INSTANCE, PLAYER, "gold", online, claim::set);

        assertTrue(claim.get().successful(), claim.get().detail());
        assertEquals(1, withdrawals.get());
    }

    @Test
    void deliveryCapacityRaceFailsWithoutMailboxPendingResult() {
        PlayerMock online = new PlayerMock(MockBukkit.getMock(), "Claimant", PLAYER);
        MockBukkit.getMock().addPlayer(online);
        RewardClaimService claims = new RewardClaimService(clock(), entitlements(), items(), successfulEconomy());
        AtomicReference<RewardClaimService.ClaimResult> claim = new AtomicReference<>();
        claims.claim(INSTANCE, PLAYER, "gold", online, claim::set);

        assertTrue(claim.get().successful(), claim.get().detail());
        ItemStack[] full = new ItemStack[online.getInventory().getSize()];
        java.util.Arrays.fill(full, new ItemStack(Material.STONE, 64));
        online.getInventory().setContents(full);
        AtomicReference<RewardClaimService.DeliveryResult> delivery = new AtomicReference<>();

        claims.deliver(INSTANCE, PLAYER, online, delivery::set);

        assertFalse(delivery.get().successful());
        assertFalse(delivery.get().pending());
        assertEquals("inventory is full; make room before delivery retry", delivery.get().detail());
        UUID offerId = claim.get().offerId();
        assertEquals(OfferState.OWNED,
                claims.info(INSTANCE, PLAYER).orElseThrow().offers().get(offerId).state());
    }

    @Test
    void deliveryPauseLeavesOwnedClaimForRestartJoinRecovery() {
        PlayerMock online = new PlayerMock(MockBukkit.getMock(), "Claimant", PLAYER);
        MockBukkit.getMock().addPlayer(online);
        RewardClaimService claims = new RewardClaimService(clock(), entitlements(), items(), successfulEconomy());
        claims.setDeliveryPausedForTesting(true);
        AtomicReference<RewardClaimService.ClaimResult> claim = new AtomicReference<>();

        claims.claim(INSTANCE, PLAYER, "gold", online, claim::set);

        AtomicReference<RewardClaimService.DeliveryResult> pausedDelivery = new AtomicReference<>();
        claims.deliver(INSTANCE, PLAYER, online, pausedDelivery::set);

        assertTrue(claim.get().successful(), claim.get().detail());
        assertTrue(pausedDelivery.get().pending());
        UUID offerId = claim.get().offerId();
        assertEquals(OfferState.OWNED,
                claims.info(INSTANCE, PLAYER).orElseThrow().offers().get(offerId).state());

        claims.setDeliveryPausedForTesting(false);
        AtomicReference<RewardClaimService.DeliveryResult> recoveredDelivery = new AtomicReference<>();
        claims.deliverPending(online, recoveredDelivery::set);
        assertTrue(recoveredDelivery.get().successful());
        assertEquals(OfferState.DELIVERED,
                claims.info(INSTANCE, PLAYER).orElseThrow().offers().get(offerId).state());
        assertEquals(1, online.getInventory().all(Material.GOLD_INGOT).values().stream()
                .mapToInt(ItemStack::getAmount).sum());

        claims.deliverPending(online);
        assertEquals(1, online.getInventory().all(Material.GOLD_INGOT).values().stream()
                .mapToInt(ItemStack::getAmount).sum());
    }

    private static RewardEntitlementService entitlements() {
        RewardDefinition gold = new RewardDefinition(true, 5, 0, 1, false,
                List.of(new RewardItem("GOLD", 1, 1, 1)));
        RewardDefinition wood = new RewardDefinition(true, 0, 0, 1, false,
                List.of(new RewardItem("WOOD", 1, 1, 1)));
        RewardEntitlementService service = new RewardEntitlementService(clock(),
                Set.of("GOLD", "WOOD")::contains);
        ScoreService.ScoreResult score = new ScoreService.ScoreResult(100, 100, 100, 0, 300,
                DungeonRank.S_PLUS, List.of());
        ScoreService.FinalScoreSnapshot finalScore = new ScoreService.FinalScoreSnapshot(
                new ScoreService.ScoreInput(true, 0, Duration.ofMinutes(1), 0, 0),
                score.skill(), score.time(), score.exploration(), score.bonus(), score.total(),
                score.rank(), score.bonusFacts());
        service.register(new RewardEntitlementService.Completion(INSTANCE, 42, COMPLETED, finalScore,
                List.of(new RewardEntitlementService.Participant(PLAYER, true, true)),
                Map.of("gold", gold, "wood", wood)));
        return service;
    }

    private static CaveItemsGateway items() {
        return new CaveItemsGateway() {
            @Override
            public boolean isConfigured(String itemId) { return true; }

            @Override
            public java.util.Optional<ItemStack> build(String itemId, int amount) {
                return java.util.Optional.of(new ItemStack(itemId.equals("GOLD") ? Material.GOLD_INGOT : Material.OAK_LOG,
                        amount));
            }
        };
    }

    private static EconomyGateway successfulEconomy() {
        return new EconomyGateway() {
            @Override
            public String providerIdentity() { return "TestEconomy"; }

            @Override
            public TransactionResult withdraw(OfflinePlayer player, double amount) {
                return new TransactionResult(true, amount, 10, "charged");
            }

            @Override
            public TransactionResult deposit(OfflinePlayer player, double amount) {
                return new TransactionResult(true, amount, 10, "credited");
            }
        };
    }

    private static OfflinePlayer player() {
        OfflinePlayer player = Mockito.mock(OfflinePlayer.class);
        Mockito.when(player.getUniqueId()).thenReturn(PLAYER);
        return player;
    }

    private static Clock clock() {
        return Clock.fixed(COMPLETED.plusSeconds(10), ZoneOffset.UTC);
    }

    private static final class InMemoryRepository implements DurableRepository {
        private final Map<String, DurableRecord> records = new HashMap<>();
        private int failRuntimeAckVersion = -1;

        @Override
        public boolean reserveTerminalLane(UUID instanceId) { return true; }

        @Override
        public void releaseTerminalLane(UUID instanceId) { }

        @Override
        public DurableSubmission submit(DurableWrite write) {
            String key = write.namespace() + ":" + write.recordId();
            records.put(key, new DurableRecord(write.namespace(), write.recordId(), write.payload(), "checksum",
                    Path.of("memory-record")));
            DurableWriteReceipt receipt = new DurableWriteReceipt(write.operationId(), write.idempotencyKey(),
                    write.recordVersion(), "checksum", Path.of("memory-record"), Instant.EPOCH);
            CompletableFuture<DurableWriteReceipt> ack = write.recordVersion() == failRuntimeAckVersion
                    ? CompletableFuture.failedFuture(new IllegalStateException("injected runtime ACK failure"))
                    : CompletableFuture.completedFuture(receipt);
            return new DurableSubmission(true, CompletableFuture.completedFuture(receipt), ack, "accepted");
        }

        @Override
        public DurableSubmission submitTerminal(DurableWrite write) { return submit(write); }

        @Override
        public CompletableFuture<Optional<DurableRecord>> read(String namespace, String recordId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(records.get(namespace + ":" + recordId)));
        }

        @Override
        public CompletableFuture<List<DurableRecord>> list(String namespace) {
            return CompletableFuture.completedFuture(records.values().stream()
                    .filter(record -> record.namespace().equals(namespace)).toList());
        }

        @Override
        public CompletableFuture<Void> delete(String namespace, String recordId) {
            records.remove(namespace + ":" + recordId);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public RepositoryDiagnostics diagnostics() {
            return new RepositoryDiagnostics(1, 0, 0, 0, 0, false);
        }

        @Override
        public void close() { }
    }
}
