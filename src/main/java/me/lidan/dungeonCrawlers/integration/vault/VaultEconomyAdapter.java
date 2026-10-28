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
        return observe(player, economy.withdrawPlayer(player, amount), false);
    }

    @Override
    public TransactionResult deposit(OfflinePlayer player, double amount) {
        return observe(player, economy.depositPlayer(player, amount), true);
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
