package me.lidan.dungeonCrawlers.integration.vault;

import me.lidan.dungeonCrawlers.integration.EconomyGateway;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.util.Objects;
import java.util.Optional;

public final class VaultEconomyAdapter implements EconomyGateway {
    private final Economy economy;

    public VaultEconomyAdapter(Economy economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    @Override
    public String providerIdentity() {
        return economy.getName();
    }

    @Override
    public Optional<Boolean> hasFunds(OfflinePlayer player, double amount) {
        return Optional.of(economy.has(player, amount));
    }

    @Override
    public TransactionResult withdraw(OfflinePlayer player, double amount) {
        if (!prepare(player, amount, false)) return new TransactionResult(false, 0, economy.getBalance(player), "transaction cancelled");
        return observe(player, economy.withdrawPlayer(player, amount), false);
    }

    @Override
    public TransactionResult deposit(OfflinePlayer player, double amount) {
        if (!prepare(player, amount, true)) return new TransactionResult(false, 0, economy.getBalance(player), "transaction cancelled");
        return observe(player, economy.depositPlayer(player, amount), true);
    }

    private boolean prepare(OfflinePlayer player, double amount, boolean created) {
        return amount <= 0 || !Double.isFinite(amount)
                || new me.lidan.cavecrawlers.utils.CurrencyTransactionPrepareEvent(player.getUniqueId(), amount,
                        created, created ? "dungeon_vault_credit" : "dungeon_vault_debit", "dungeoncrawlers").callEvent();
    }

    private TransactionResult observe(OfflinePlayer player, EconomyResponse response, boolean created) {
        TransactionResult result = map(response);
        if (response.transactionSuccess() && response.amount > 0 && Double.isFinite(response.amount)) {
            org.bukkit.Bukkit.getPluginManager().callEvent(
                    new me.lidan.dungeonCrawlers.events.DungeonCurrencyTransactionEvent(
                            player.getUniqueId(), response.amount, created));
        }
        return result;
    }

    private TransactionResult map(EconomyResponse response) {
        return new TransactionResult(
                response.transactionSuccess(), response.amount, response.balance,
                response.errorMessage == null ? response.type.name() : response.errorMessage
        );
    }
}
