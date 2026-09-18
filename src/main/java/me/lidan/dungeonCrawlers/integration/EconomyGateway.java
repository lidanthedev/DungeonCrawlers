package me.lidan.dungeonCrawlers.integration;

import org.bukkit.OfflinePlayer;

import java.util.Optional;

public interface EconomyGateway {
    String providerIdentity();

    /** Returns whether the provider confirms that the account can afford the amount, when supported. */
    default Optional<Boolean> hasFunds(OfflinePlayer player, double amount) {
        return Optional.empty();
    }

    /** Returns a definite no-debit result for a normal provider rejection; null/throws are ambiguous. */
    TransactionResult withdraw(OfflinePlayer player, double amount);

    TransactionResult deposit(OfflinePlayer player, double amount);

    record TransactionResult(boolean successful, double amount, double balance, String detail) {}
}
