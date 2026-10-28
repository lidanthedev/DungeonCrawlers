package me.lidan.dungeonCrawlers.integration.vault;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import net.milkbowl.vault.economy.*;
import me.lidan.dungeonCrawlers.events.DungeonCurrencyTransactionEvent;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultEconomyAdapterTest {
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
            verifyNoInteractions(events);
            when(economy.withdrawPlayer(account, 10)).thenReturn(new EconomyResponse(10, 5, EconomyResponse.ResponseType.SUCCESS, null));
            assertTrue(adapter.withdraw(account, 10).successful());
            var capture = org.mockito.ArgumentCaptor.forClass(DungeonCurrencyTransactionEvent.class);
            verify(events).callEvent(capture.capture());
            assertEquals(10, capture.getValue().getAmount());
            assertFalse(capture.getValue().isCreated());
            when(economy.depositPlayer(account, 0)).thenReturn(new EconomyResponse(0, 5, EconomyResponse.ResponseType.SUCCESS, null));
            adapter.deposit(account, 0);
            verifyNoMoreInteractions(events);
        }
    }
}
