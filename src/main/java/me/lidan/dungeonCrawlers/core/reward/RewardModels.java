package me.lidan.dungeonCrawlers.core.reward;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RewardModels {
    private RewardModels() {
    }

    public record ItemPayload(UUID itemId, String caveItemId, int amount, byte[] serializedItem, String checksum) {
        public ItemPayload {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(caveItemId, "caveItemId");
            Objects.requireNonNull(serializedItem, "serializedItem");
            Objects.requireNonNull(checksum, "checksum");
            if (amount < 1 || caveItemId.isBlank() || checksum.isBlank()) {
                throw new IllegalArgumentException("invalid item payload");
            }
            serializedItem = serializedItem.clone();
        }

        @Override
        public byte[] serializedItem() {
            return serializedItem.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ItemPayload that && amount == that.amount && itemId.equals(that.itemId)
                    && caveItemId.equals(that.caveItemId) && checksum.equals(that.checksum)
                    && java.util.Arrays.equals(serializedItem, that.serializedItem);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(itemId, caveItemId, amount, checksum);
            return 31 * result + java.util.Arrays.hashCode(serializedItem);
        }
    }

    public record Mailbox(UUID ownerId, List<ItemPayload> pending) {
        public Mailbox {
            Objects.requireNonNull(ownerId, "ownerId");
            pending = List.copyOf(pending);
        }
    }
}
