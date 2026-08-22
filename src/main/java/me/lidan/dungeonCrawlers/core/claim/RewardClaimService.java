package me.lidan.dungeonCrawlers.core.claim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import me.lidan.dungeonCrawlers.core.reward.RewardEntitlementService;
import me.lidan.dungeonCrawlers.core.reward.RewardModels.ItemPayload;
import me.lidan.dungeonCrawlers.core.reward.RewardRoller;
import me.lidan.dungeonCrawlers.integration.CaveItemsGateway;
import me.lidan.dungeonCrawlers.integration.EconomyGateway;
import me.lidan.dungeonCrawlers.persistence.DurableRecord;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.DurableSubmission;
import me.lidan.dungeonCrawlers.persistence.DurableWrite;
import me.lidan.dungeonCrawlers.persistence.model.CompletedRunRecordCodec;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns the paid reward claim boundary.
 *
 * <p>The entitlement service freezes the offer and rolls. This service turns one frozen offer
 * into an immutable payload, durably locks the player's claim group, performs at most one Vault
 * call, and only then moves the payload through protected inventory delivery. New purchases
 * reject before payment when the player's storage inventory cannot fit the payload. Durable
 * pending state remains only for interrupted or legacy deliveries. All repository callbacks are
 * marshalled through {@code mainThread}; the repository worker never touches Bukkit objects.</p>
 */
public final class RewardClaimService {
    public static final String NAMESPACE = "reward-claims";
    public static final int SCHEMA_VERSION = 1;
    public static final String DEFAULT_PROVIDER = "Vault";

    private static final NamespacedKey CLAIM_KEY = new NamespacedKey("dungeoncrawlers", "reward-claim");
    private static final NamespacedKey ITEM_KEY = new NamespacedKey("dungeoncrawlers", "reward-item");
    private static final NamespacedKey OWNER_KEY = new NamespacedKey("dungeoncrawlers", "reward-owner");
    private static final NamespacedKey PENDING_KEY = new NamespacedKey("dungeoncrawlers", "reward-pending");
    private static final String INVENTORY_FULL_DETAIL =
            "inventory is full; make room before purchasing";
    private static final String INSUFFICIENT_FUNDS_DETAIL = "not enough money for this reward";

    private final Clock clock;
    private final DurableRepository repository;
    private final RewardEntitlementService entitlements;
    private final CaveItemsGateway caveItems;
    private final Supplier<EconomyGateway> economy;
    private final Executor mainThread;
    private final Consumer<String> auditSink;
    private final OfferStateMachine states = new OfferStateMachine();
    private final Gson gson;
    private final Map<String, MutableRecord> records = new LinkedHashMap<>();
    private final Map<UUID, String> offerIndex = new LinkedHashMap<>();
    private final CompletableFuture<Void> restored = new CompletableFuture<>();
    private volatile boolean deliveryPausedForTesting;

    public RewardClaimService(Clock clock, DurableRepository repository,
                              RewardEntitlementService entitlements, CaveItemsGateway caveItems,
                              Supplier<EconomyGateway> economy, Executor mainThread,
                              Consumer<String> auditSink) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.repository = repository;
        this.entitlements = Objects.requireNonNull(entitlements, "entitlements");
        this.caveItems = Objects.requireNonNull(caveItems, "caveItems");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        this.gson = new GsonBuilder().disableHtmlEscaping()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .registerTypeAdapter(byte[].class, new CompletedRunRecordCodec.ByteArrayAdapter())
                .create();
        restoreAsync();
    }

    public RewardClaimService(Clock clock, RewardEntitlementService entitlements,
                              CaveItemsGateway caveItems, EconomyGateway economy) {
        this(clock, null, entitlements, caveItems, () -> economy, Runnable::run, ignored -> { });
    }

    public CompletableFuture<Void> ready() {
        return restored;
    }

    /**
     * Enables the development-server pause used to place a durable OWNED claim before restart.
     * This flag is intentionally process-local and resets when the plugin is recreated.
     */
    public void setDeliveryPausedForTesting(boolean paused) {
        deliveryPausedForTesting = paused;
    }

    public boolean deliveryPausedForTesting() {
        return deliveryPausedForTesting;
    }

    /** Starts a claim. The callback is always invoked on the configured main-thread executor. */
    public void claim(UUID instanceId, UUID playerId, String rewardId, OfflinePlayer account,
                      Consumer<ClaimResult> callback) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardId, "rewardId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(callback, "callback");
        if (!playerId.equals(account.getUniqueId())) {
            complete(callback, ClaimResult.failure(instanceId, playerId, null,
                    ClaimStatus.REJECTED, "claim account does not match player"));
            return;
        }
        whenRestored(() -> beginClaim(instanceId, playerId, rewardId, account, callback),
                () -> complete(callback, ClaimResult.failure(instanceId, playerId, null,
                        ClaimStatus.PERSISTENCE_FAILED, "reward claim service did not restore")));
    }

    /** Applies an audited operator decision to a financially unresolved offer. */
    public void reconcile(UUID claimId, Decision decision, String operator, String evidence,
                          Consumer<ReconcileResult> callback) {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(callback, "callback");
        whenRestored(() -> reconcileInternal(claimId, decision, operator, evidence, callback),
                () -> complete(callback, ReconcileResult.failure(claimId, "reward claim service did not restore")));
    }

    /** Delivers owned or pending rewards without using CaveCrawlers' unsafe give methods. */
    public void deliver(UUID instanceId, UUID playerId, Player player, Consumer<DeliveryResult> callback) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(callback, "callback");
        if (!playerId.equals(player.getUniqueId())) {
            complete(callback, DeliveryResult.failure(instanceId, playerId, null,
                    "delivery player does not match owner"));
            return;
        }
        if (deliveryPausedForTesting) {
            complete(callback, DeliveryResult.pending(instanceId, playerId, null, 0, 0,
                    "delivery paused for restart test; reconnect to resume"));
            return;
        }
        whenRestored(() -> deliverInternal(instanceId, playerId, player, callback),
                () -> complete(callback, DeliveryResult.failure(instanceId, playerId, null,
                        "reward claim service did not restore")));
    }

    /** Retries every owned/pending/quarantined claim for a player, normally called on join. */
    public void deliverPending(Player player) {
        deliverPending(player, result -> { });
    }

    /** Retries every owned/pending/quarantined claim and reports each recovery result on the main thread. */
    public void deliverPending(Player player, Consumer<DeliveryResult> callback) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(callback, "callback");
        whenRestored(() -> {
            clearLegacyDeliveryMarkers(player);
            List<UUID> instances;
            synchronized (records) {
                instances = records.values().stream()
                        .filter(record -> record.current.playerId().equals(player.getUniqueId()))
                        .filter(record -> record.current.offers().values().stream()
                                .anyMatch(offer -> offer.state() == OfferState.OWNED
                                        || offer.state() == OfferState.DELIVERY_PENDING
                                        || offer.state() == OfferState.OWNED_DELIVERY_QUARANTINED))
                        .map(record -> record.current.instanceId()).distinct().toList();
            }
            instances.forEach(instance -> deliver(instance, player.getUniqueId(), player, result -> {
                if (!result.successful() && !result.pending()) {
                    auditSink.accept("reward delivery retry failed instance=" + instance + ": " + result.detail());
                }
                callback.accept(result);
            }));
        }, () -> { });
    }

    /** Removes an instance's in-memory claim record for the explicit admin test reset. */
    public void clearInstance(UUID instanceId) {
        synchronized (records) {
            records.values().removeIf(record -> {
                if (!record.current.instanceId().equals(instanceId)) return false;
                removeIndexes(record.current);
                return true;
            });
        }
    }

    /** Clears the explicit test instance and removes its durable claim records before reuse. */
    public void resetInstance(UUID instanceId, Consumer<Boolean> callback) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(callback, "callback");
        clearInstance(instanceId);
        if (repository == null) {
            complete(callback, true);
            return;
        }
        repository.list(NAMESPACE).whenComplete((values, failure) -> {
            if (failure != null) {
                dispatch(() -> complete(callback, false));
                return;
            }
            List<CompletableFuture<Void>> deletes = new ArrayList<>();
            try {
                for (DurableRecord value : values) {
                    ClaimRecord record = decode(value.payload());
                    if (record.instanceId().equals(instanceId)) deletes.add(repository.delete(NAMESPACE,
                            value.recordId()));
                }
            } catch (RuntimeException exception) {
                dispatch(() -> complete(callback, false));
                return;
            }
            CompletableFuture.allOf(deletes.toArray(CompletableFuture[]::new)).whenComplete((ignored, deleteFailure) ->
                    dispatch(() -> complete(callback, deleteFailure == null)));
        });
    }

    public Optional<ClaimRecord> info(UUID instanceId, UUID playerId) {
        synchronized (records) {
            MutableRecord record = records.get(recordKey(instanceId, playerId));
            return record == null ? Optional.empty() : Optional.of(record.current);
        }
    }

    public List<ClaimRecord> records(UUID instanceId) {
        synchronized (records) {
            return records.values().stream().map(value -> value.current)
                    .filter(value -> value.instanceId().equals(instanceId)).toList();
        }
    }

    public static boolean isPending(ItemStack item) {
        return readBoolean(item, PENDING_KEY);
    }

    public static Optional<UUID> claimId(ItemStack item) {
        return readUuid(item, CLAIM_KEY);
    }

    public static Optional<UUID> itemId(ItemStack item) {
        return readUuid(item, ITEM_KEY);
    }

    public static Optional<UUID> ownerId(ItemStack item) {
        return readUuid(item, OWNER_KEY);
    }

    public static ItemStack markPending(ItemStack source, UUID claimId, UUID ownerId, UUID itemId) {
        Objects.requireNonNull(source, "source");
        ItemStack result = source.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("reward item has no item metadata");
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(CLAIM_KEY, PersistentDataType.STRING, claimId.toString());
        data.set(OWNER_KEY, PersistentDataType.STRING, ownerId.toString());
        data.set(ITEM_KEY, PersistentDataType.STRING, itemId.toString());
        data.set(PENDING_KEY, PersistentDataType.BYTE, (byte) 1);
        result.setItemMeta(meta);
        return result;
    }

    public static ItemStack clearPending(ItemStack source) {
        Objects.requireNonNull(source, "source");
        ItemStack result = source.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;
        meta.getPersistentDataContainer().remove(PENDING_KEY);
        result.setItemMeta(meta);
        return result;
    }

    private void restoreAsync() {
        if (repository == null) {
            restored.complete(null);
            return;
        }
        repository.list(NAMESPACE).whenComplete((values, failure) -> dispatch(() -> {
            if (failure != null) {
                restored.completeExceptionally(unwrap(failure));
                return;
            }
            try {
                for (DurableRecord record : values) {
                    ClaimRecord decoded = decode(record.payload());
                    if (!record.recordId().equals(recordKey(decoded.instanceId(), decoded.playerId()))) {
                        throw new IllegalStateException("claim record ID does not match instance/player");
                    }
                    ClaimRecord recovered = recoverDebitAttempts(decoded);
                    MutableRecord prior = new MutableRecord(recovered);
                    synchronized (records) {
                        records.put(record.recordId(), prior);
                        indexRecord(recovered);
                    }
                }
                restored.complete(null);
            } catch (RuntimeException exception) {
                restored.completeExceptionally(exception);
            }
        }));
    }

    private ClaimRecord recoverDebitAttempts(ClaimRecord record) {
        Map<UUID, OfferSnapshot> offers = new LinkedHashMap<>(record.offers());
        List<AuditEntry> audit = new ArrayList<>(record.audit());
        boolean changed = false;
        for (Map.Entry<UUID, OfferSnapshot> entry : record.offers().entrySet()) {
            OfferSnapshot offer = entry.getValue();
            if (offer.state() != OfferState.DEBIT_ATTEMPTED) continue;
            OfferSnapshot recovered = states.recoverAfterRestart(offer, clock.instant());
            offers.put(entry.getKey(), recovered);
            audit.add(AuditEntry.system(clock.instant(), offer.offerId(), offer.attemptId(),
                    "RESTART_RECONCILIATION", "debit attempt requires operator decision"));
            changed = true;
        }
        if (!changed) return record;
        return new ClaimRecord(record.instanceId(), record.playerId(), offers, record.claimGroup(),
                record.mailbox(), audit, record.recordVersion());
    }

    private void beginClaim(UUID instanceId, UUID playerId, String rewardId, OfflinePlayer account,
                            Consumer<ClaimResult> callback) {
        RewardEntitlementService.PlayerEntitlement entitlement = entitlements.open(instanceId, playerId).orElse(null);
        RewardEntitlementService.RewardOffer reward = entitlement == null ? null : entitlement.offers().get(rewardId);
        if (entitlement == null || reward == null) {
            complete(callback, ClaimResult.failure(instanceId, playerId, null, ClaimStatus.REJECTED,
                    "reward entitlement is unavailable or expired"));
            return;
        }
        if (reward.locked()) {
            complete(callback, ClaimResult.failure(instanceId, playerId, reward.offerId(), ClaimStatus.REJECTED,
                    "score requirement not met"));
            return;
        }

        MutableRecord mutable;
        ClaimRecord current;
        synchronized (records) {
            mutable = records.get(recordKey(instanceId, playerId));
            if (mutable != null && mutable.pending) {
                complete(callback, ClaimResult.failure(instanceId, playerId, reward.offerId(),
                        ClaimStatus.PROCESSING, "another claim is still processing"));
                return;
            }
            current = mutable == null ? createRecord(instanceId, playerId, entitlement) : mutable.current;
            if (mutable != null && current.claimGroup().state() != ClaimGroup.State.NONE) {
                ClaimStatus status = current.claimGroup().state() == ClaimGroup.State.ATTEMPTED
                        ? ClaimStatus.RECONCILIATION_REQUIRED : ClaimStatus.ALREADY_CLAIMED;
                complete(callback, ClaimResult.failure(instanceId, playerId, reward.offerId(), status,
                        "this completion reward chest has already been selected"));
                return;
            }
            OfferSnapshot snapshot = current.offers().get(reward.offerId());
            if (snapshot == null) {
                complete(callback, ClaimResult.failure(instanceId, playerId, reward.offerId(),
                        ClaimStatus.REJECTED, "reward terms are no longer available"));
                return;
            }
            if (snapshot.state() == OfferState.OFFER_BLOCKED_PROVIDER) {
                EconomyGateway provider = safeEconomy();
                if (provider != null && providerIdentity(provider).equals(snapshot.provider())) {
                    OfferSnapshot repaired = transition(snapshot, OfferStateMachine.Event.PROVIDER_RESTORED, null);
                    ClaimRecord target = replaceOffer(current, repaired, current.claimGroup(),
                            audit(current, "PROVIDER_RESTORED", reward.offerId(), null,
                                    "exact economy provider restored"));
                    mutable = ensureMutable(mutable, target);
                    MutableRecord repairRecord = mutable;
                    persistCandidate(repairRecord, current, target, false, success -> {
                        if (!success) complete(callback, ClaimResult.failure(instanceId, playerId, reward.offerId(),
                                ClaimStatus.PERSISTENCE_FAILED, "could not persist provider repair"));
                        else beginClaim(instanceId, playerId, rewardId, account, callback);
                    });
                    return;
                }
            }
            if (snapshot.state() == OfferState.OFFER_PAYLOAD_QUARANTINED) {
                PayloadBuild repair = validatePayloads(snapshot.items());
                if (repair.successful() && snapshot.isOpen(clock.instant())) {
                    OfferSnapshot repaired = transition(snapshot, OfferStateMachine.Event.PAYLOAD_REPAIRED, null);
                    ClaimRecord target = replaceOffer(current, repaired, current.claimGroup(),
                            audit(current, "PAYLOAD_REPAIRED", reward.offerId(), null,
                                    "exact serialized payload validated"));
                    mutable = ensureMutable(mutable, target);
                    MutableRecord repairRecord = mutable;
                    persistCandidate(repairRecord, current, target, false, success -> {
                        if (!success) complete(callback, ClaimResult.failure(instanceId, playerId, reward.offerId(),
                                ClaimStatus.PERSISTENCE_FAILED, "could not persist payload repair"));
                        else beginClaim(instanceId, playerId, rewardId, account, callback);
                    });
                    return;
                }
            }
            if (snapshot.state() != OfferState.AVAILABLE) {
                ClaimStatus status = snapshot.state() == OfferState.RECONCILIATION_REQUIRED
                        || snapshot.state() == OfferState.DEBIT_ATTEMPTED
                        ? ClaimStatus.RECONCILIATION_REQUIRED
                        : snapshot.state() == OfferState.EXPIRED ? ClaimStatus.EXPIRED : ClaimStatus.REJECTED;
                complete(callback, ClaimResult.failure(instanceId, playerId, reward.offerId(), status,
                        "reward offer is in state " + snapshot.state()));
                return;
            }
            if (!snapshot.isOpen(clock.instant())) {
                OfferSnapshot expired = transition(snapshot, OfferStateMachine.Event.DEADLINE_REACHED, null);
                ClaimRecord target = replaceOffer(current, expired, current.claimGroup(),
                        audit(current, "EXPIRED", reward.offerId(), null, "claim deadline reached"));
                mutable = ensureMutable(mutable, target);
                persistCandidate(mutable, current, target, false,
                        success -> complete(callback, success
                                ? ClaimResult.failure(instanceId, playerId, reward.offerId(), ClaimStatus.EXPIRED,
                                "reward claim window has expired")
                                : ClaimResult.failure(instanceId, playerId, reward.offerId(),
                                ClaimStatus.PERSISTENCE_FAILED, "could not persist reward expiry")));
                return;
            }

            EconomyGateway provider = safeEconomy();
            String providerId = providerIdentity(provider);
            OfferSnapshot prepared = snapshot;
            PayloadBuild payloads = payloadsFor(reward, snapshot.items());
            if (payloads.successful() && account instanceof Player player
                    && !canAcceptPayloads(player, payloads.items(), reward.offerId(), current.playerId())) {
                complete(callback, ClaimResult.failure(instanceId, playerId, reward.offerId(),
                        ClaimStatus.REJECTED, INVENTORY_FULL_DETAIL));
                return;
            }
            if (payloads.successful()) prepared = snapshot.withItems(payloads.items());
            OfferStateMachine.Event preflight = reward.price() > 0
                    && (provider == null || !providerId.equals(snapshot.provider()))
                    ? OfferStateMachine.Event.PROVIDER_MISSING
                    : payloads.successful() ? null : OfferStateMachine.Event.PAYLOAD_INVALID;
            if (preflight != null) {
                OfferSnapshot blocked = transition(prepared, preflight, null);
                ClaimRecord target = replaceOffer(current, blocked, current.claimGroup(),
                        audit(current, blocked.state().name(), reward.offerId(), null, payloads.detail()));
                mutable = ensureMutable(mutable, target);
                persistCandidate(mutable, current, target, false, success -> complete(callback,
                        success ? ClaimResult.failure(instanceId, playerId, reward.offerId(),
                                blocked.state() == OfferState.OFFER_BLOCKED_PROVIDER
                                        ? ClaimStatus.BLOCKED_PROVIDER : ClaimStatus.BLOCKED_PAYLOAD,
                                payloads.detail()) : ClaimResult.failure(instanceId, playerId, reward.offerId(),
                                ClaimStatus.PERSISTENCE_FAILED, "could not persist reward preflight")));
                return;
            }

            ClaimRecord preparedRecord = replaceOffer(current, prepared, current.claimGroup(),
                    audit(current, "PREPARED", reward.offerId(), null, "payload and provider preflight passed"));
            mutable = ensureMutable(mutable, preparedRecord);
            MutableRecord selected = mutable;
            persistCandidate(selected, current, preparedRecord, false, preparedAck -> {
                if (!preparedAck) {
                    complete(callback, ClaimResult.failure(instanceId, playerId, reward.offerId(),
                            ClaimStatus.PERSISTENCE_FAILED, "could not persist reward preparation"));
                    return;
                }
                startDebit(selected, account, reward.offerId(), callback);
            });
        }
    }

    private void startDebit(MutableRecord mutable, OfflinePlayer account, UUID offerId,
                            Consumer<ClaimResult> callback) {
        ClaimRecord current;
        OfferSnapshot source;
        UUID attempt = UUID.randomUUID();
        synchronized (records) {
            current = mutable.current;
            source = current.offers().get(offerId);
            if (source == null || source.state() != OfferState.AVAILABLE
                    || current.claimGroup().state() != ClaimGroup.State.NONE) {
                complete(callback, ClaimResult.failure(current.instanceId(), current.playerId(), offerId,
                        ClaimStatus.PROCESSING, "reward claim changed while preparing"));
                return;
            }
            OfferSnapshot attempted = transition(source, OfferStateMachine.Event.CONFIRM_DEBIT, attempt);
            ClaimGroup group = current.claimGroup().attempt(offerId, attempt);
            ClaimRecord target = replaceOffer(current, attempted, group,
                    audit(current, "DEBIT_ATTEMPTED", offerId, attempt, "durable debit attempt created"));
            persistCandidate(mutable, current, target, true, attemptAck -> {
                if (!attemptAck) {
                    complete(callback, ClaimResult.failure(current.instanceId(), current.playerId(), offerId,
                            ClaimStatus.RECONCILIATION_REQUIRED,
                            "debit attempt persistence is uncertain; reconcile the claim"));
                    return;
                }
                executeDebit(mutable, account, offerId, attempt, callback);
            });
        }
    }

    private void executeDebit(MutableRecord mutable, OfflinePlayer account, UUID offerId, UUID attempt,
                              Consumer<ClaimResult> callback) {
        ClaimRecord current = mutable.current;
        OfferSnapshot offer = current.offers().get(offerId);
        if (offer == null || offer.state() != OfferState.DEBIT_ATTEMPTED
                || !attempt.equals(offer.attemptId())) {
            complete(callback, ClaimResult.failure(current.instanceId(), current.playerId(), offerId,
                    ClaimStatus.RECONCILIATION_REQUIRED, "stale debit callback was ignored"));
            return;
        }

        EconomyGateway.TransactionResult transaction;
        if (account instanceof Player player
                && !canAcceptPayloads(player, offer.items(), offerId, current.playerId())) {
            transaction = new EconomyGateway.TransactionResult(false, 0, 0, INVENTORY_FULL_DETAIL);
        } else {
            EconomyGateway provider = safeEconomy();
            if (offer.price() == 0) {
                transaction = new EconomyGateway.TransactionResult(true, 0, 0, "free reward");
            } else if (provider == null || !providerIdentity(provider).equals(offer.provider())) {
                transaction = null;
            } else {
                try {
                    double amount = offer.price();
                    transaction = provider.withdraw(account, amount);
                    if (transaction == null) {
                        markAmbiguous(mutable, offerId, attempt, "provider returned no debit result", callback);
                        return;
                    }
                    if (transaction.successful()
                            && (!Double.isFinite(transaction.amount()) || transaction.amount() < 0)) {
                        String detail = "provider returned an invalid debit result: " + transaction.detail();
                        markAmbiguous(mutable, offerId, attempt, detail, callback);
                        return;
                    }
                } catch (RuntimeException exception) {
                    auditSink.accept("reward debit ambiguous offer=" + offerId + " attempt=" + attempt
                            + " provider=" + offer.provider() + ": " + exception.getMessage());
                    markAmbiguous(mutable, offerId, attempt, "Vault call threw: " + exception.getMessage(), callback);
                    return;
                }
            }
        }

        if (transaction == null) {
            markAmbiguous(mutable, offerId, attempt, "provider disappeared during debit", callback);
            return;
        }
        if (!transaction.successful()) {
            EconomyGateway.TransactionResult failedTransaction = transaction;
            OfferSnapshot available = transition(offer, OfferStateMachine.Event.DEBIT_FAILED, null);
            ClaimGroup group = current.claimGroup().release(offerId, attempt);
            ClaimRecord target = replaceOffer(current, available, group,
                    audit(current, "DEBIT_FAILED", offerId, attempt, failedTransaction.detail()));
            persistCandidate(mutable, current, target, false, success -> complete(callback,
                    success ? ClaimResult.failure(current.instanceId(), current.playerId(), offerId,
                            ClaimStatus.REJECTED, INSUFFICIENT_FUNDS_DETAIL)
                            : ClaimResult.failure(current.instanceId(), current.playerId(), offerId,
                            ClaimStatus.RECONCILIATION_REQUIRED,
                            "purchase failed but state persistence is uncertain")));
            return;
        }

        ClaimRecord owned = ownershipRecord(current, offerId, attempt, "debit succeeded");
        persistCandidate(mutable, current, owned, false, success -> complete(callback,
                success ? ClaimResult.success(current.instanceId(), current.playerId(), offerId,
                        OfferState.OWNED, "reward purchased")
                        : ClaimResult.failure(current.instanceId(), current.playerId(), offerId,
                        ClaimStatus.RECONCILIATION_REQUIRED,
                        "currency was charged or may have been charged; reconcile the claim")));
    }

    private void markAmbiguous(MutableRecord mutable, UUID offerId, UUID attempt, String detail,
                               Consumer<ClaimResult> callback) {
        ClaimRecord current = mutable.current;
        OfferSnapshot source = current.offers().get(offerId);
        if (source == null || source.state() != OfferState.DEBIT_ATTEMPTED) return;
        OfferSnapshot ambiguous = transition(source, OfferStateMachine.Event.DEBIT_AMBIGUOUS, attempt);
        ClaimRecord target = replaceOffer(current, ambiguous, current.claimGroup(),
                audit(current, "RECONCILIATION_REQUIRED", offerId, attempt, detail));
        persistCandidate(mutable, current, target, true, success -> complete(callback,
                ClaimResult.failure(current.instanceId(), current.playerId(), offerId,
                        ClaimStatus.RECONCILIATION_REQUIRED, detail)));
    }

    private void reconcileInternal(UUID claimId, Decision decision, String operator, String evidence,
                                   Consumer<ReconcileResult> callback) {
        MutableRecord mutable;
        OfferSnapshot source;
        synchronized (records) {
            String key = offerIndex.get(claimId);
            mutable = key == null ? null : records.get(key);
            source = mutable == null ? null : mutable.current.offers().get(claimId);
            if (mutable == null || source == null) {
                complete(callback, ReconcileResult.failure(claimId, "claim offer not found"));
                return;
            }
            if (mutable.pending || source.state() != OfferState.RECONCILIATION_REQUIRED
                    || mutable.current.claimGroup().state() != ClaimGroup.State.ATTEMPTED
                    || !claimId.equals(mutable.current.claimGroup().winnerOfferId())) {
                complete(callback, ReconcileResult.failure(claimId,
                        "only an ATTEMPTED/RECONCILIATION_REQUIRED claim can be reconciled"));
                return;
            }
            UUID attempt = source.attemptId();
            if (decision == Decision.NOT_CHARGED) {
                OfferSnapshot targetOffer = transition(source, OfferStateMachine.Event.RECONCILE_NOT_CHARGED, attempt);
                ClaimGroup targetGroup = mutable.current.claimGroup().release(claimId, attempt);
                ClaimRecord target = replaceOffer(mutable.current, targetOffer, targetGroup,
                        audit(mutable.current, "RECONCILE_NOT_CHARGED", claimId, attempt,
                                operator + ": " + evidence));
                persistCandidate(mutable, mutable.current, target, false, success -> complete(callback,
                        success ? ReconcileResult.success(claimId, targetOffer.state(), "claim released")
                                : ReconcileResult.failure(claimId, "could not persist reconciliation")));
                return;
            }
            ClaimRecord target = ownershipRecord(mutable.current, claimId, attempt,
                    "RECONCILE_CHARGED " + operator + ": " + evidence);
            persistCandidate(mutable, mutable.current, target, false, success -> complete(callback,
                    success ? ReconcileResult.success(claimId, OfferState.OWNED, "ownership restored")
                            : ReconcileResult.failure(claimId, "could not persist charged reconciliation")));
        }
    }

    private ClaimRecord ownershipRecord(ClaimRecord current, UUID offerId, UUID attempt, String detail) {
        OfferSnapshot source = current.offers().get(offerId);
        OfferStateMachine.Event event = source.state() == OfferState.DEBIT_ATTEMPTED
                ? OfferStateMachine.Event.DEBIT_SUCCEEDED : OfferStateMachine.Event.RECONCILE_CHARGED;
        OfferSnapshot owned = transition(source, event, attempt);
        Map<UUID, OfferSnapshot> offers = new LinkedHashMap<>(current.offers());
        offers.put(offerId, owned);
        for (Map.Entry<UUID, OfferSnapshot> entry : current.offers().entrySet()) {
            if (entry.getKey().equals(offerId)) continue;
            OfferSnapshot sibling = entry.getValue();
            if (sibling.state() == OfferState.AVAILABLE
                    || sibling.state() == OfferState.OFFER_BLOCKED_PROVIDER
                    || sibling.state() == OfferState.OFFER_PAYLOAD_QUARANTINED) {
                offers.put(entry.getKey(), transition(sibling, OfferStateMachine.Event.SIBLING_CLAIMED, null));
            }
        }
        Map<UUID, List<ItemPayload>> mailbox = new LinkedHashMap<>(current.mailbox());
        mailbox.put(offerId, owned.items());
        ClaimGroup group = current.claimGroup().state() == ClaimGroup.State.ATTEMPTED
                ? current.claimGroup().claim(offerId, attempt) : current.claimGroup();
        return new ClaimRecord(current.instanceId(), current.playerId(), offers, group, mailbox,
                audit(current, "OWNED", offerId, attempt, detail), current.recordVersion() + 1);
    }

    private void deliverInternal(UUID instanceId, UUID playerId, Player player,
                                 Consumer<DeliveryResult> callback) {
        MutableRecord mutable;
        synchronized (records) {
            mutable = records.get(recordKey(instanceId, playerId));
            if (mutable == null) {
                complete(callback, DeliveryResult.failure(instanceId, playerId, null, "reward claim not found"));
                return;
            }
            if (mutable.pending) {
                complete(callback, DeliveryResult.failure(instanceId, playerId, null, "claim is still processing"));
                return;
            }
            UUID offerId = mutable.current.offers().values().stream()
                    .filter(offer -> offer.state() == OfferState.DELIVERY_PENDING || offer.state() == OfferState.OWNED
                            || offer.state() == OfferState.OWNED_DELIVERY_QUARANTINED)
                    .map(OfferSnapshot::offerId).findFirst().orElse(null);
            if (offerId == null) {
                complete(callback, DeliveryResult.failure(instanceId, playerId, null,
                        "no owned reward is ready for delivery"));
                return;
            }
            OfferSnapshot source = mutable.current.offers().get(offerId);
            if (source.state() == OfferState.OWNED_DELIVERY_QUARANTINED) {
                PayloadBuild repair = validatePayloads(source.items());
                if (!repair.successful()) {
                    complete(callback, DeliveryResult.failure(instanceId, playerId, offerId,
                            "owned payload remains quarantined: " + repair.detail()));
                    return;
                }
                OfferSnapshot repaired = transition(source, OfferStateMachine.Event.PAYLOAD_REPAIRED, null);
                ClaimRecord target = replaceOffer(mutable.current, repaired, mutable.current.claimGroup(),
                        audit(mutable.current, "PAYLOAD_REPAIRED", offerId, source.attemptId(),
                                "owned payload repair validated"));
                persistCandidate(mutable, mutable.current, target, true, success -> {
                    if (!success) complete(callback, DeliveryResult.failure(instanceId, playerId, offerId,
                            "could not persist owned payload repair"));
                    else deliverInternal(instanceId, playerId, player, callback);
                });
                return;
            }
            PayloadBuild validated = validatePayloads(source.items());
            if (!validated.successful()) {
                OfferSnapshot quarantined = transition(source, OfferStateMachine.Event.PAYLOAD_INVALID, null);
                ClaimRecord target = replaceOffer(mutable.current, quarantined, mutable.current.claimGroup(),
                        audit(mutable.current, "OWNED_DELIVERY_QUARANTINED", offerId, source.attemptId(), validated.detail()));
                persistCandidate(mutable, mutable.current, target, true, success -> complete(callback,
                        DeliveryResult.failure(instanceId, playerId, offerId,
                                "reward payload is quarantined: " + validated.detail())));
                return;
            }
            ClaimRecord current = mutable.current;
            List<ItemPayload> deliveryPayloads = current.mailbox().getOrDefault(offerId, source.items());
            if (!canAcceptPayloads(player, deliveryPayloads, offerId, current.playerId())) {
                complete(callback, DeliveryResult.failure(instanceId, playerId, offerId,
                        "inventory is full; make room before delivery retry"));
                return;
            }
            ClaimRecord pending = current;
            if (source.state() == OfferState.OWNED) {
                OfferSnapshot requested = transition(source, OfferStateMachine.Event.REQUEST_DELIVERY, null);
                Map<UUID, List<ItemPayload>> mailbox = new LinkedHashMap<>(current.mailbox());
                mailbox.putIfAbsent(offerId, source.items());
                pending = replaceOffer(current, requested, current.claimGroup(),
                        mailbox, audit(current, "DELIVERY_PENDING", offerId, source.attemptId(), "delivery reserved"));
            }
            ClaimRecord target = pending;
            persistCandidate(mutable, current, target, true, success -> {
                if (!success) {
                    complete(callback, DeliveryResult.failure(instanceId, playerId, offerId,
                            "could not persist delivery reservation"));
                    return;
                }
                insertPending(mutable, offerId, player, callback);
            });
        }
    }

    private void insertPending(MutableRecord mutable, UUID offerId, Player player,
                               Consumer<DeliveryResult> callback) {
        ClaimRecord current = mutable.current;
        OfferSnapshot offer = current.offers().get(offerId);
        List<ItemPayload> pending = current.mailbox().getOrDefault(offerId, offer.items());
        List<ItemPayload> remaining = new ArrayList<>();
        int inserted = 0;
        try {
            for (ItemPayload payload : pending) {
                DeliveryScan scan = scan(player, offerId, payload.itemId());
                if (scan.unsafe()) throw new IllegalStateException(scan.detail());
                int existing = scan.amount();
                if (existing >= payload.amount()) {
                    inserted += payload.amount();
                    continue;
                }
                ItemStack stack = ItemStack.deserializeBytes(payload.serializedItem());
                if (stack == null || stack.getAmount() < 1) throw new IllegalStateException("item deserialized empty");
                stack.setAmount(payload.amount() - existing);
                stack = markPending(stack, offerId, current.playerId(), payload.itemId());
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
                int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                int delivered = stack.getAmount() - leftoverAmount;
                inserted += Math.max(0, delivered);
                if (leftoverAmount > 0) {
                    ItemStack leftover = leftovers.values().iterator().next();
                    remaining.add(payloadFromStack(payload, leftover));
                }
            }
        } catch (RuntimeException exception) {
            OfferSnapshot quarantined = transition(offer, OfferStateMachine.Event.DELIVERY_UNSAFE, null);
            ClaimRecord target = replaceOffer(current, quarantined, current.claimGroup(),
                    audit(current, "OWNED_DELIVERY_QUARANTINED", offerId, offer.attemptId(), exception.getMessage()));
            persistCandidate(mutable, current, target, true, ignored -> complete(callback,
                    DeliveryResult.failure(current.instanceId(), current.playerId(), offerId,
                            "delivery was quarantined: " + exception.getMessage())));
            return;
        }

        final int insertedAmount = inserted;
        Map<UUID, List<ItemPayload>> mailbox = new LinkedHashMap<>(current.mailbox());
        if (remaining.isEmpty()) mailbox.remove(offerId);
        else mailbox.put(offerId, List.copyOf(remaining));
        if (!remaining.isEmpty()) {
            OfferSnapshot blocked = transition(offer, OfferStateMachine.Event.DELIVERY_UNSAFE, null);
            ClaimRecord target = replaceOffer(current, blocked, current.claimGroup(), mailbox,
                    audit(current, "DELIVERY_BLOCKED", offerId, offer.attemptId(),
                            "inventory capacity changed before delivery completed"));
            persistCandidate(mutable, current, target, true, success -> complete(callback,
                    success ? DeliveryResult.failure(current.instanceId(), current.playerId(), offerId,
                            "inventory became full; make room before delivery retry")
                            : DeliveryResult.failure(current.instanceId(), current.playerId(), offerId,
                            "could not persist delivery block")));
            return;
        }

        OfferSnapshot delivered = transition(offer, OfferStateMachine.Event.DELIVERY_VERIFIED, null);
        ClaimRecord target = replaceOffer(current, delivered, current.claimGroup(), mailbox,
                audit(current, "DELIVERED", offerId, offer.attemptId(), "inventory insertion verified"));
        persistCandidate(mutable, current, target, true, success -> {
            if (!success) {
                complete(callback, DeliveryResult.pending(current.instanceId(), current.playerId(), offerId,
                        insertedAmount, 1, "delivery is pending durable verification"));
                return;
            }
            clearDeliveryMarkers(player, offerId);
            complete(callback, DeliveryResult.success(current.instanceId(), current.playerId(), offerId,
                    insertedAmount, "reward delivered"));
        });
    }

    private DeliveryScan scan(Player player, UUID claimId, UUID expectedItemId) {
        int amount = 0;
        for (ItemStack item : allItems(player)) {
            if (item == null || !expectedItemId.equals(RewardClaimService.itemId(item).orElse(null))) continue;
            UUID owner = ownerId(item).orElse(null);
            UUID claim = RewardClaimService.claimId(item).orElse(null);
            if (!player.getUniqueId().equals(owner) || !claimId.equals(claim)) {
                return new DeliveryScan(0, true, "reward item provenance belongs to another owner/claim");
            }
            amount += item.getAmount();
        }
        return new DeliveryScan(amount, false, "");
    }

    private void clearDeliveryMarkers(Player player, UUID expectedClaimId) {
        clearDeliveryMarkers(player.getInventory(), expectedClaimId);
        clearDeliveryMarkers(player.getEnderChest(), expectedClaimId);
    }

    private void clearDeliveryMarkers(Inventory inventory, UUID expectedClaimId) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && expectedClaimId.equals(RewardClaimService.claimId(item).orElse(null))) {
                inventory.setItem(slot, clearProvenance(item));
            }
        }
    }

    /** Removes markers left by the pre-Phase-12 delivery implementation after a reload or join. */
    private void clearLegacyDeliveryMarkers(Player player) {
        clearLegacyDeliveryMarkers(player.getInventory());
        clearLegacyDeliveryMarkers(player.getEnderChest());
    }

    private void clearLegacyDeliveryMarkers(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !isPending(item) && hasProvenance(item)) {
                inventory.setItem(slot, clearProvenance(item));
            }
        }
    }

    private static boolean hasProvenance(ItemStack item) {
        return claimId(item).isPresent() || itemId(item).isPresent() || ownerId(item).isPresent();
    }

    private static ItemStack clearProvenance(ItemStack source) {
        Objects.requireNonNull(source, "source");
        ItemStack result = source.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.remove(CLAIM_KEY);
        data.remove(ITEM_KEY);
        data.remove(OWNER_KEY);
        data.remove(PENDING_KEY);
        result.setItemMeta(meta);
        return result;
    }

    private static List<ItemStack> allItems(Player player) {
        List<ItemStack> result = new ArrayList<>();
        result.addAll(Arrays.asList(player.getInventory().getContents()));
        result.addAll(Arrays.asList(player.getEnderChest().getContents()));
        return result;
    }

    private PayloadBuild payloadsFor(RewardEntitlementService.RewardOffer reward, List<ItemPayload> existing) {
        if (!existing.isEmpty()) return validatePayloads(existing);
        if (reward.rolls().isEmpty()) return PayloadBuild.failure("reward contains no materialized items");
        List<ItemPayload> result = new ArrayList<>();
        int index = 0;
        try {
            for (RewardRoller.RolledReward rolled : reward.rolls()) {
                ItemStack probe = caveItems.build(rolled.itemId(), 1).orElse(null);
                if (probe == null) return PayloadBuild.failure("CaveItems could not build " + rolled.itemId());
                int maxStack = Math.max(1, probe.getMaxStackSize());
                int remaining = rolled.amount();
                while (remaining > 0) {
                    int amount = Math.min(maxStack, remaining);
                    ItemStack built = caveItems.build(rolled.itemId(), amount).orElse(null);
                    if (built == null) return PayloadBuild.failure("CaveItems could not build " + rolled.itemId());
                    built = built.clone();
                    built.setAmount(amount);
                    byte[] bytes = built.serializeAsBytes();
                    ItemStack restored = ItemStack.deserializeBytes(bytes);
                    if (restored == null || !built.equals(restored)) {
                        return PayloadBuild.failure("CaveItems payload did not round-trip for " + rolled.itemId());
                    }
                    UUID itemId = UUID.nameUUIDFromBytes((reward.offerId() + ":" + index++)
                            .getBytes(StandardCharsets.UTF_8));
                    result.add(new ItemPayload(itemId, rolled.itemId(), amount, bytes, checksum(bytes)));
                    remaining -= amount;
                }
            }
        } catch (RuntimeException exception) {
            return PayloadBuild.failure("item payload preflight failed: " + exception.getMessage());
        }
        return new PayloadBuild(List.copyOf(result), true, "payload preflight passed");
    }

    private PayloadBuild validatePayloads(List<ItemPayload> payloads) {
        try {
            for (ItemPayload payload : payloads) {
                if (!checksum(payload.serializedItem()).equals(payload.checksum())) {
                    return PayloadBuild.failure("payload checksum mismatch for " + payload.itemId());
                }
                ItemStack restored = ItemStack.deserializeBytes(payload.serializedItem());
                if (restored == null || restored.getAmount() != payload.amount()) {
                    return PayloadBuild.failure("payload amount mismatch for " + payload.itemId());
                }
            }
            return new PayloadBuild(List.copyOf(payloads), true, "payload preflight passed");
        } catch (RuntimeException exception) {
            return PayloadBuild.failure("payload validation failed: " + exception.getMessage());
        }
    }

    private static boolean canAcceptPayloads(Player player, List<ItemPayload> payloads,
                                             UUID claimId, UUID ownerId) {
        List<ItemStack> simulated = new ArrayList<>(Arrays.asList(player.getInventory().getStorageContents()));
        for (int index = 0; index < simulated.size(); index++) {
            ItemStack item = simulated.get(index);
            simulated.set(index, item == null ? null : item.clone());
        }
        for (ItemPayload payload : payloads) {
            ItemStack reward = ItemStack.deserializeBytes(payload.serializedItem());
            if (reward == null) return false;
            try {
                reward = markPending(reward, claimId, ownerId, payload.itemId());
            } catch (RuntimeException exception) {
                return false;
            }
            int remaining = payload.amount();
            for (ItemStack existing : simulated) {
                if (remaining == 0) break;
                if (existing == null || !existing.isSimilar(reward)) continue;
                int stackLimit = Math.min(existing.getMaxStackSize(), reward.getMaxStackSize());
                int available = Math.max(0, stackLimit - existing.getAmount());
                int added = Math.min(available, remaining);
                existing.setAmount(existing.getAmount() + added);
                remaining -= added;
            }
            while (remaining > 0) {
                int emptySlot = -1;
                for (int index = 0; index < simulated.size(); index++) {
                    if (simulated.get(index) == null) {
                        emptySlot = index;
                        break;
                    }
                }
                if (emptySlot < 0) return false;
                int amount = Math.min(reward.getMaxStackSize(), remaining);
                ItemStack inserted = reward.clone();
                inserted.setAmount(amount);
                simulated.set(emptySlot, inserted);
                remaining -= amount;
            }
        }
        return true;
    }

    private static ItemPayload payloadFromStack(ItemPayload source, ItemStack stack) {
        byte[] bytes = stack.serializeAsBytes();
        return new ItemPayload(source.itemId(), source.caveItemId(), stack.getAmount(), bytes, checksum(bytes));
    }

    private ClaimRecord createRecord(UUID instanceId, UUID playerId,
                                     RewardEntitlementService.PlayerEntitlement entitlement) {
        RewardEntitlementService.RunSnapshot run = entitlements.info(instanceId).orElseThrow(
                () -> new IllegalStateException("reward entitlement disappeared during claim"));
        String provider = providerIdentity(safeEconomy());
        Map<UUID, OfferSnapshot> offers = new LinkedHashMap<>();
        for (RewardEntitlementService.RewardOffer reward : entitlement.offers().values()) {
            offers.put(reward.offerId(), new OfferSnapshot(reward.offerId(), entitlement.mode(), OfferState.AVAILABLE,
                    null, run.completedAt(), entitlement.mode() == OfferMode.RECOVERED ? run.completedAt() : null,
                    entitlement.mode() == OfferMode.RECOVERED ? entitlement.outerDeadline() : null,
                    entitlement.sessionStartedAt(), entitlement.sessionExpiresAt(),
                    highWater(run.completedAt()), null, provider, playerId, reward.price(), List.of()));
        }
        return new ClaimRecord(instanceId, playerId, offers, ClaimGroup.none(), Map.of(), List.of(), 0);
    }

    private Instant highWater(Instant completedAt) {
        Instant now = clock.instant();
        return now.isAfter(completedAt) ? now : completedAt;
    }

    private static ClaimRecord replaceOffer(ClaimRecord source, OfferSnapshot offer, ClaimGroup group,
                                            List<AuditEntry> audit) {
        return replaceOffer(source, offer, group, source.mailbox(), audit);
    }

    private static ClaimRecord replaceOffer(ClaimRecord source, OfferSnapshot offer, ClaimGroup group,
                                            Map<UUID, List<ItemPayload>> mailbox, List<AuditEntry> audit) {
        Map<UUID, OfferSnapshot> offers = new LinkedHashMap<>(source.offers());
        offers.put(offer.offerId(), offer);
        return new ClaimRecord(source.instanceId(), source.playerId(), offers, group, mailbox, audit,
                source.recordVersion() + 1);
    }

    private List<AuditEntry> audit(ClaimRecord source, String action, UUID offerId, UUID attempt, String detail) {
        List<AuditEntry> result = new ArrayList<>(source.audit());
        result.add(AuditEntry.system(clock.instant(), offerId, attempt, action, detail));
        return List.copyOf(result);
    }

    private MutableRecord ensureMutable(MutableRecord mutable, ClaimRecord target) {
        if (mutable != null) return mutable;
        mutable = new MutableRecord(target);
        synchronized (records) {
            records.put(recordKey(target.instanceId(), target.playerId()), mutable);
            indexRecord(target);
        }
        return mutable;
    }

    private void persistCandidate(MutableRecord mutable, ClaimRecord before, ClaimRecord target,
                                  boolean retainOnAckFailure, Consumer<Boolean> callback) {
        synchronized (records) {
            if (mutable.pending) {
                complete(callback, false);
                return;
            }
            mutable.pending = true;
            mutable.current = target;
            indexRecord(target);
        }
        if (repository == null) {
            synchronized (records) { mutable.pending = false; }
            complete(callback, true);
            return;
        }
        DurableSubmission submission;
        try {
            DurableWrite write = new DurableWrite(UUID.randomUUID(), target.instanceId(), NAMESPACE,
                    recordKey(target.instanceId(), target.playerId()),
                    "reward-claim-" + recordKey(target.instanceId(), target.playerId()) + "-v" + target.recordVersion(),
                    target.recordVersion(), encode(target));
            submission = repository.submit(write);
        } catch (RuntimeException exception) {
            revert(mutable, before);
            complete(callback, false);
            return;
        }
        if (!submission.accepted()) {
            revert(mutable, before);
            complete(callback, false);
            return;
        }
        submission.runtimeAck().whenComplete((receipt, failure) -> dispatch(() -> {
            if (failure != null) {
                if (!retainOnAckFailure) revert(mutable, before);
                else synchronized (records) { mutable.pending = false; }
                auditSink.accept("reward durable ACK failed record=" + recordKey(target.instanceId(), target.playerId())
                        + " version=" + target.recordVersion() + ": " + unwrap(failure).getMessage());
                complete(callback, false);
                return;
            }
            synchronized (records) { mutable.pending = false; }
            complete(callback, true);
        }));
    }

    private void revert(MutableRecord mutable, ClaimRecord before) {
        synchronized (records) {
            mutable.current = before;
            mutable.pending = false;
            indexRecord(before);
        }
    }

    private void whenRestored(Runnable action, Runnable failureAction) {
        restored.whenComplete((ignored, failure) -> dispatch(() -> {
            if (failure != null) {
                failureAction.run();
                return;
            }
            action.run();
        }));
    }

    private void dispatch(Runnable runnable) {
        try {
            mainThread.execute(runnable);
        } catch (RuntimeException exception) {
            auditSink.accept("reward main-thread callback rejected: " + exception.getMessage());
        }
    }

    private <T> void complete(Consumer<T> callback, T value) {
        try {
            callback.accept(value);
        } catch (RuntimeException exception) {
            auditSink.accept("reward callback failed: " + exception.getMessage());
        }
    }

    private EconomyGateway safeEconomy() {
        try {
            return economy.get();
        } catch (RuntimeException exception) {
            auditSink.accept("economy provider lookup failed: " + exception.getMessage());
            return null;
        }
    }

    private static String providerIdentity(EconomyGateway provider) {
        if (provider == null) return DEFAULT_PROVIDER;
        try {
            String identity = provider.providerIdentity();
            return identity == null || identity.isBlank() ? DEFAULT_PROVIDER : identity;
        } catch (RuntimeException exception) {
            return DEFAULT_PROVIDER;
        }
    }

    private byte[] encode(ClaimRecord record) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("schema-version", SCHEMA_VERSION);
        envelope.add("record", gson.toJsonTree(record));
        return gson.toJson(envelope).getBytes(StandardCharsets.UTF_8);
    }

    private ClaimRecord decode(byte[] payload) {
        try {
            JsonElement parsed = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new JsonParseException("claim payload must be an object");
            JsonObject envelope = parsed.getAsJsonObject();
            if (envelope.get("schema-version") == null
                    || envelope.get("schema-version").getAsInt() != SCHEMA_VERSION) {
                throw new JsonParseException("unsupported reward claim schema-version");
            }
            ClaimRecord result = gson.fromJson(envelope.get("record"), ClaimRecord.class);
            if (result == null || result.recordVersion() < 0) throw new JsonParseException("invalid reward claim record");
            return result;
        } catch (JsonParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JsonParseException("invalid reward claim payload", exception);
        }
    }

    private static String recordKey(UUID instanceId, UUID playerId) {
        return UUID.nameUUIDFromBytes((instanceId + ":" + playerId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void indexRecord(ClaimRecord record) {
        for (UUID offerId : record.offers().keySet()) offerIndex.put(offerId, recordKey(record.instanceId(), record.playerId()));
    }

    private void removeIndexes(ClaimRecord record) {
        record.offers().keySet().forEach(offerIndex::remove);
    }

    private OfferSnapshot transition(OfferSnapshot source, OfferStateMachine.Event event, UUID attempt) {
        OfferStateMachine.TransitionResult result = states.transition(source, event, clock.instant(), attempt);
        if (!result.accepted()) throw new IllegalStateException(result.detail());
        return result.snapshot();
    }

    private static String checksum(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static boolean readBoolean(ItemStack item, NamespacedKey key) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        Byte value = meta == null ? null : meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    private static Optional<UUID> readUuid(ItemStack item, NamespacedKey key) {
        if (item == null) return Optional.empty();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();
        String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        try {
            return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public enum Decision { CHARGED, NOT_CHARGED }

    public enum ClaimStatus {
        CLAIMED, REJECTED, PROCESSING, BLOCKED_PROVIDER, BLOCKED_PAYLOAD,
        RECONCILIATION_REQUIRED, ALREADY_CLAIMED, EXPIRED, PERSISTENCE_FAILED
    }

    public record ClaimResult(UUID instanceId, UUID playerId, UUID offerId, ClaimStatus status, String detail) {
        public ClaimResult {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
        }

        public static ClaimResult success(UUID instanceId, UUID playerId, UUID offerId,
                                          OfferState state, String detail) {
            return new ClaimResult(instanceId, playerId, offerId, state == OfferState.OWNED
                    ? ClaimStatus.CLAIMED : ClaimStatus.REJECTED, detail);
        }

        public static ClaimResult failure(UUID instanceId, UUID playerId, UUID offerId,
                                          ClaimStatus status, String detail) {
            return new ClaimResult(instanceId, playerId, offerId, status, detail);
        }

        public boolean successful() { return status == ClaimStatus.CLAIMED; }

        public boolean insufficientFunds() {
            return status == ClaimStatus.REJECTED && INSUFFICIENT_FUNDS_DETAIL.equals(detail);
        }
    }

    public record ReconcileResult(UUID offerId, boolean successful, OfferState state, String detail) {
        public ReconcileResult {
            Objects.requireNonNull(offerId, "offerId");
            Objects.requireNonNull(detail, "detail");
        }

        static ReconcileResult success(UUID id, OfferState state, String detail) {
            return new ReconcileResult(id, true, state, detail);
        }

        static ReconcileResult failure(UUID id, String detail) {
            return new ReconcileResult(id, false, null, detail);
        }
    }

    public record DeliveryResult(UUID instanceId, UUID playerId, UUID offerId, boolean successful,
                                 boolean pending, int insertedAmount, int pendingStacks, String detail) {
        public DeliveryResult {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(detail, "detail");
        }

        static DeliveryResult success(UUID instance, UUID player, UUID offer, int inserted, String detail) {
            return new DeliveryResult(instance, player, offer, true, false, inserted, 0, detail);
        }

        static DeliveryResult pending(UUID instance, UUID player, UUID offer, int inserted, int stacks, String detail) {
            return new DeliveryResult(instance, player, offer, false, true, inserted, stacks, detail);
        }

        static DeliveryResult failure(UUID instance, UUID player, UUID offer, String detail) {
            return new DeliveryResult(instance, player, offer, false, false, 0, 0, detail);
        }
    }

    public record AuditEntry(Instant at, UUID offerId, UUID attemptId, String action, String detail) {
        public AuditEntry {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(detail, "detail");
        }

        static AuditEntry system(Instant at, UUID offerId, UUID attemptId, String action, String detail) {
            return new AuditEntry(at, offerId, attemptId, action, detail == null ? "" : detail);
        }
    }

    public record ClaimRecord(UUID instanceId, UUID playerId, Map<UUID, OfferSnapshot> offers,
                              ClaimGroup claimGroup, Map<UUID, List<ItemPayload>> mailbox,
                              List<AuditEntry> audit, long recordVersion) {
        public ClaimRecord {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(offers, "offers");
            Objects.requireNonNull(claimGroup, "claimGroup");
            Objects.requireNonNull(mailbox, "mailbox");
            Objects.requireNonNull(audit, "audit");
            if (recordVersion < 0) throw new IllegalArgumentException("record version must not be negative");
            offers = Map.copyOf(offers);
            mailbox = mailbox.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
            audit = List.copyOf(audit);
        }
    }

    private static final class MutableRecord {
        private ClaimRecord current;
        private boolean pending;

        private MutableRecord(ClaimRecord current) {
            this.current = current;
        }
    }

    private record PayloadBuild(List<ItemPayload> items, boolean successful, String detail) {
        private static PayloadBuild failure(String detail) {
            return new PayloadBuild(List.of(), false, detail == null ? "payload preflight failed" : detail);
        }
    }

    private record DeliveryScan(int amount, boolean unsafe, String detail) { }

    private static final class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant source, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(source.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                return Instant.parse(json.getAsString());
            } catch (RuntimeException exception) {
                throw new JsonParseException("invalid reward claim instant", exception);
            }
        }
    }
}
