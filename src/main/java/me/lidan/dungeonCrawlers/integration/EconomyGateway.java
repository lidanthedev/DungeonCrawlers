package me.lidan.dungeonCrawlers.integration;

import org.bukkit.OfflinePlayer;

public interface EconomyGateway {
    String providerIdentity();

    TransactionResult withdraw(OfflinePlayer player, double amount);

    TransactionResult deposit(OfflinePlayer player, double amount);

    record TransactionResult(boolean successful, double amount, double balance, String detail) {}
}

