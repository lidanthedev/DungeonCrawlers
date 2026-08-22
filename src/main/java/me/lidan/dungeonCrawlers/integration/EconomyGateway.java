package me.lidan.dungeonCrawlers.integration;

import org.bukkit.OfflinePlayer;

public interface EconomyGateway {
    String providerIdentity();

    /** Returns a definite no-debit result for a normal provider rejection; null/throws are ambiguous. */
    TransactionResult withdraw(OfflinePlayer player, double amount);

    TransactionResult deposit(OfflinePlayer player, double amount);

    record TransactionResult(boolean successful, double amount, double balance, String detail) {}
}
