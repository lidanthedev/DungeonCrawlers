package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.core.claim.RewardClaimService;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class RewardDeliveryMessages {
    private RewardDeliveryMessages() { }

    public static void send(Player player, RewardClaimService.DeliveryResult delivery) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(delivery, "delivery");
        String message = delivery.successful()
                ? "<green>Reward delivered to your inventory.</green>"
                : delivery.pending()
                ? "<yellow>Reward delivery pending: " + delivery.detail() + "</yellow>"
                : "<red>Reward delivery failed: " + delivery.detail() + "</red>";
        player.sendMessage(MiniMessageUtils.miniMessage(message));
    }
}
