package me.lidan.dungeonCrawlers.integration.vault;

import me.lidan.cavecrawlers.utils.CurrencyTransactionPrepareEvent;
import me.lidan.dungeonCrawlers.events.DungeonCurrencyTransactionEvent;
import net.milkbowl.vault.economy.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultEconomyAdapterTest {
    @Test void cancelledRequestNeverCallsTheProvider() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var events = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            doAnswer(invocation -> {
                ((CurrencyTransactionPrepareEvent) invocation.getArgument(0)).setCancelled(true);
                return null;
            }).when(events).callEvent(any(CurrencyTransactionPrepareEvent.class));
            var account = mock(OfflinePlayer.class);
            when(account.getUniqueId()).thenReturn(UUID.randomUUID());
            var economy = mock(Economy.class);
            var adapter = new VaultEconomyAdapter(economy);
            assertFalse(adapter.withdraw(account, 10).successful());
            assertFalse(adapter.deposit(account, 10).successful());
            verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
            verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
            verify(events, never()).callEvent(any(DungeonCurrencyTransactionEvent.class));
        }
    }

    @Test void onlyCompletedNonzeroTransactionsPublish() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var events = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var account = mock(OfflinePlayer.class);
            when(account.getUniqueId()).thenReturn(UUID.randomUUID());
            var economy = mock(Economy.class);
            var adapter = new VaultEconomyAdapter(economy);
            when(economy.withdrawPlayer(account, 10)).thenReturn(new EconomyResponse(0, 5, EconomyResponse.ResponseType.FAILURE, "denied"));
            assertFalse(adapter.withdraw(account, 10).successful());
            verify(events, never()).callEvent(any(DungeonCurrencyTransactionEvent.class));
            clearInvocations(events);
            when(economy.withdrawPlayer(account, 10)).thenReturn(new EconomyResponse(10, 5, EconomyResponse.ResponseType.SUCCESS, null));
            assertTrue(adapter.withdraw(account, 10).successful());
            var capture = ArgumentCaptor.forClass(DungeonCurrencyTransactionEvent.class);
            verify(events).callEvent(capture.capture());
            assertEquals(10, capture.getValue().getAmount());
            assertFalse(capture.getValue().isCreated());
            when(economy.depositPlayer(account, 0)).thenReturn(new EconomyResponse(0, 5, EconomyResponse.ResponseType.SUCCESS, null));
            adapter.deposit(account, 0);
            verify(events).callEvent(any(CurrencyTransactionPrepareEvent.class));
            verifyNoMoreInteractions(events);
        }
    }
}
