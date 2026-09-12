package me.lidan.dungeonCrawlers.integration;

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
                ? "<yellow>Reward delivery pending: " + pendingMessage(delivery.detail()) + "</yellow>"
                : "<red>Reward delivery failed: " + failureMessage(delivery.detail()) + "</red>";
        DungeonMessages.send(player, message);
    }

    private static String pendingMessage(String detail) {
        String normalized = normalize(detail);
        if (normalized.contains("paused for restart test")) {
            return "your reward remains reserved while recovery testing is active.";
        }
        if (normalized.contains("durable verification")) {
            return "your reward is being verified and will be delivered shortly.";
        }
        return "your reward remains reserved and will be delivered when recovery completes.";
    }

    private static String failureMessage(String detail) {
        String normalized = normalize(detail);
        if (normalized.contains("inventory is full") || normalized.contains("inventory became full")) {
            return "your inventory is full. Make room, then try again.";
        }
        if (normalized.contains("no owned reward") || normalized.contains("claim not found")) {
            return "no undelivered reward was found.";
        }
        if (normalized.contains("quarantined") || normalized.contains("provenance")) {
            return "your reward needs staff review. Please contact an administrator.";
        }
        if (normalized.contains("did not restore") || normalized.contains("could not persist")
                || normalized.contains("persist delivery")) {
            return "reward delivery is temporarily unavailable. Please try again later.";
        }
        if (normalized.contains("does not match owner")) {
            return "reward ownership could not be verified.";
        }
        return "your reward could not be delivered. Please contact an administrator.";
    }

    private static String normalize(String detail) {
        return detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
    }
}
