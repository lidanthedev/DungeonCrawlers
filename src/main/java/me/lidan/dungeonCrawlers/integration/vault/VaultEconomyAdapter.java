package me.lidan.dungeonCrawlers.integration.vault;

import me.lidan.dungeonCrawlers.integration.EconomyGateway;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.util.Objects;

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
    public TransactionResult withdraw(OfflinePlayer player, double amount) {
        return map(economy.withdrawPlayer(player, amount));
    }

    @Override
    public TransactionResult deposit(OfflinePlayer player, double amount) {
        return map(economy.depositPlayer(player, amount));
    }

    private TransactionResult map(EconomyResponse response) {
        return new TransactionResult(
                response.transactionSuccess(), response.amount, response.balance,
                response.errorMessage == null ? response.type.name() : response.errorMessage
        );
    }
}

