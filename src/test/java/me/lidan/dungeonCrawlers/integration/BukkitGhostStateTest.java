package me.lidan.dungeonCrawlers.integration;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class BukkitGhostStateTest {
    @Test
    void invisibilityUsesTheRemainingGhostDuration() {
        Player player = mock(Player.class);
        when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());

        BukkitGhostState.enter(player, Duration.ofSeconds(17));

        ArgumentCaptor<PotionEffect> effect = ArgumentCaptor.forClass(PotionEffect.class);
        verify(player).addPotionEffect(effect.capture(), eq(true));
        assertEquals(PotionEffectType.INVISIBILITY, effect.getValue().getType());
        assertEquals(340, effect.getValue().getDuration());
    }

    @Test
    void exitClearsGhostInvisibility() {
        Player player = mock(Player.class);

        BukkitGhostState.exit(player);

        verify(player).removePotionEffect(PotionEffectType.INVISIBILITY);
        verify(player).setInvisible(false);
        verify(player).setInvulnerable(false);
        verify(player).setCollidable(true);
    }
}
