package me.lidan.dungeonCrawlers.persistence;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public record DurableSubmission(boolean accepted, CompletableFuture<DurableWriteReceipt> receipt,
                                CompletableFuture<DurableWriteReceipt> runtimeAck, String detail) {
    public DurableSubmission {
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(runtimeAck, "runtimeAck");
        Objects.requireNonNull(detail, "detail");
    }
}
