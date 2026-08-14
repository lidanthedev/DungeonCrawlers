package me.lidan.dungeonCrawlers.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDurableRepositoryTest {
    @TempDir Path directory;
    private final BlockingQueue<Runnable> runtimeCallbacks = new LinkedBlockingQueue<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void writesInOrderAndReadsAfterRestart() throws Exception {
        UUID instance = UUID.randomUUID();
        try (FileDurableRepository repository = repository(4, FileDurableRepository.FailureInjector.none())) {
            DurableSubmission first = repository.submit(write(instance, "first", 1));
            DurableSubmission second = repository.submit(write(instance, "second", 2));
            first.receipt().get(5, TimeUnit.SECONDS);
            second.receipt().get(5, TimeUnit.SECONDS);
            assertFalse(first.runtimeAck().isDone());
            runNextRuntimeCallback();
            runNextRuntimeCallback();
            assertTrue(second.runtimeAck().isDone());
        }
        try (FileDurableRepository restarted = repository(4, FileDurableRepository.FailureInjector.none())) {
            byte[] restored = restarted.read("instances", "record").get(5, TimeUnit.SECONDS).orElseThrow().payload();
            assertArrayEquals("second".getBytes(StandardCharsets.UTF_8), restored);
        }
    }

    @Test
    void saturationRejectsBeforeSecondSideEffect() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var blocker = (FileDurableRepository.FailureInjector) (stage, write) -> {
            if (stage == FileDurableRepository.FailureStage.WRITE) {
                entered.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
        };
        try (FileDurableRepository repository = repository(1, blocker)) {
            DurableSubmission first = repository.submit(write(UUID.randomUUID(), "first", 1));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            DurableSubmission rejected = repository.submit(write(UUID.randomUUID(), "second", 2));
            assertFalse(rejected.accepted());
            release.countDown();
            first.receipt().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void reservedTerminalLaneBypassesNormalSaturation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        UUID terminalInstance = UUID.randomUUID();
        var blocker = (FileDurableRepository.FailureInjector) (stage, write) -> {
            if (stage == FileDurableRepository.FailureStage.WRITE && !write.instanceId().equals(terminalInstance)) {
                entered.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
        };
        try (FileDurableRepository repository = repository(1, blocker)) {
            assertTrue(repository.reserveTerminalLane(terminalInstance));
            repository.submit(write(UUID.randomUUID(), "normal", 1));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            DurableSubmission terminal = repository.submitTerminal(write(terminalInstance, "terminal", 2));
            assertTrue(terminal.accepted());
            release.countDown();
            terminal.receipt().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void writeStagesFailReceiptAndAckWhileAckFailureKeepsReceipt() throws Exception {
        for (var stage : FileDurableRepository.FailureStage.values()) {
            var injector = (FileDurableRepository.FailureInjector) (candidate, write) -> {
                if (candidate == stage) throw new java.io.IOException("injected " + stage);
            };
            try (FileDurableRepository repository = repository(2, injector)) {
                DurableSubmission submission = repository.submit(write(UUID.randomUUID(), stage.name(), 1));
                if (stage == FileDurableRepository.FailureStage.SUBMIT) {
                    assertFalse(submission.accepted());
                    continue;
                }
                assertTrue(submission.accepted());
                if (stage == FileDurableRepository.FailureStage.ACK) {
                    submission.receipt().get(5, TimeUnit.SECONDS);
                    assertThrows(ExecutionException.class, () -> submission.runtimeAck().get(5, TimeUnit.SECONDS));
                } else {
                    assertThrows(ExecutionException.class, () -> submission.receipt().get(5, TimeUnit.SECONDS));
                }
            }
        }
    }

    @Test
    void filesystemStagesNeverRunOnSubmittingThread() throws Exception {
        Thread submittingThread = Thread.currentThread();
        var injector = (FileDurableRepository.FailureInjector) (stage, write) -> {
            if (stage != FileDurableRepository.FailureStage.SUBMIT) {
                assertFalse(Thread.currentThread() == submittingThread, "filesystem/ACK stage ran on submitter");
            }
        };
        try (FileDurableRepository repository = repository(2, injector)) {
            repository.submit(write(UUID.randomUUID(), "async", 1)).receipt().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void restartIdempotencyAcceptsExactRetryAndRejectsConflictOrStaleWrite() throws Exception {
        UUID instance = UUID.randomUUID();
        DurableWrite original = new DurableWrite(UUID.randomUUID(), instance, "instances", "record",
                "stable-key", 4, "payload".getBytes(StandardCharsets.UTF_8));
        try (FileDurableRepository repository = repository(4, FileDurableRepository.FailureInjector.none())) {
            repository.submit(original).receipt().get(5, TimeUnit.SECONDS);
        }
        try (FileDurableRepository restarted = repository(4, FileDurableRepository.FailureInjector.none())) {
            DurableWrite retry = new DurableWrite(UUID.randomUUID(), instance, "instances", "record",
                    "stable-key", 4, "payload".getBytes(StandardCharsets.UTF_8));
            restarted.submit(retry).receipt().get(5, TimeUnit.SECONDS);
            DurableWrite conflict = new DurableWrite(UUID.randomUUID(), instance, "instances", "record",
                    "stable-key", 4, "different".getBytes(StandardCharsets.UTF_8));
            assertThrows(ExecutionException.class,
                    () -> restarted.submit(conflict).receipt().get(5, TimeUnit.SECONDS));
            DurableWrite stale = new DurableWrite(UUID.randomUUID(), instance, "instances", "record",
                    "another-key", 3, "older".getBytes(StandardCharsets.UTF_8));
            assertThrows(ExecutionException.class,
                    () -> restarted.submit(stale).receipt().get(5, TimeUnit.SECONDS));
        }
    }

    private FileDurableRepository repository(int capacity, FileDurableRepository.FailureInjector injector) {
        return new FileDurableRepository(directory, capacity, runtimeCallbacks::add, clock, injector);
    }

    private void runNextRuntimeCallback() throws InterruptedException {
        Runnable callback = runtimeCallbacks.poll(5, TimeUnit.SECONDS);
        assertNotNull(callback, "runtime callback was not scheduled");
        callback.run();
    }

    private static DurableWrite write(UUID instance, String value, long version) {
        return new DurableWrite(UUID.randomUUID(), instance, "instances", "record", value + "-key", version,
                value.getBytes(StandardCharsets.UTF_8));
    }
}
