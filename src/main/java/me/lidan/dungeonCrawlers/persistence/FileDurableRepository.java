package me.lidan.dungeonCrawlers.persistence;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileDurableRepository implements DurableRepository {
    private static final int FILE_MAGIC = 0x44435231;
    private final Path root;
    private final int normalCapacity;
    private final Semaphore normalPermits;
    private final ThreadPoolExecutor worker;
    private final ThreadPoolExecutor terminalWorker;
    private final Executor runtimeExecutor;
    private final Clock clock;
    private final FailureInjector failures;
    private final Set<UUID> terminalReservations = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> terminalInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object submissionLock = new Object();
    private final Object commitLock = new Object();

    public FileDurableRepository(Path root, int normalCapacity, Executor runtimeExecutor) {
        this(root, normalCapacity, runtimeExecutor, Clock.systemUTC(), FailureInjector.none());
    }

    FileDurableRepository(Path root, int normalCapacity, Executor runtimeExecutor, Clock clock,
                          FailureInjector failures) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (normalCapacity < 1) throw new IllegalArgumentException("normal capacity must be positive");
        this.normalCapacity = normalCapacity;
        this.normalPermits = new Semaphore(normalCapacity);
        this.runtimeExecutor = Objects.requireNonNull(runtimeExecutor, "runtimeExecutor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.worker = newWorker("dungeoncrawlers-durable-repository");
        this.terminalWorker = newWorker("dungeoncrawlers-terminal-repository");
    }

    private static ThreadPoolExecutor newWorker(String name) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public boolean reserveTerminalLane(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        synchronized (submissionLock) {
            return !closed.get() && terminalReservations.add(instanceId);
        }
    }

    @Override
    public void releaseTerminalLane(UUID instanceId) {
        synchronized (submissionLock) {
            if (terminalInFlight.contains(instanceId)) {
                throw new IllegalStateException("terminal write is still in flight");
            }
            terminalReservations.remove(instanceId);
        }
    }

    @Override
    public DurableSubmission submit(DurableWrite write) {
        return enqueue(write, false);
    }

    @Override
    public DurableSubmission submitTerminal(DurableWrite write) {
        return enqueue(write, true);
    }

    private DurableSubmission enqueue(DurableWrite write, boolean terminal) {
        Objects.requireNonNull(write, "write");
        CompletableFuture<DurableWriteReceipt> receipt = new CompletableFuture<>();
        CompletableFuture<DurableWriteReceipt> runtimeAck = new CompletableFuture<>();
        synchronized (submissionLock) {
            if (closed.get()) return rejected(receipt, runtimeAck, "repository is closed");
            try {
                failures.before(FailureStage.SUBMIT, write);
            } catch (Exception exception) {
                return rejected(receipt, runtimeAck, "submission failure: " + exception.getMessage());
            }
            if (terminal) {
                if (!terminalReservations.contains(write.instanceId())) {
                    return rejected(receipt, runtimeAck, "no terminal lane reserved for instance");
                }
                if (!terminalInFlight.add(write.instanceId())) {
                    return rejected(receipt, runtimeAck, "terminal write already in flight");
                }
            } else if (!normalPermits.tryAcquire()) {
                return rejected(receipt, runtimeAck, "repository queue is saturated");
            }
            RepositoryTask task = new RepositoryTask(write, terminal, receipt, runtimeAck);
            try {
                (terminal ? terminalWorker : worker).execute(task);
                return new DurableSubmission(true, receipt, runtimeAck, terminal ? "terminal write accepted" : "write accepted");
            } catch (RejectedExecutionException exception) {
                task.reject(new RejectedExecutionException("repository rejected operation", exception));
                return new DurableSubmission(false, receipt, runtimeAck, "repository rejected operation");
            }
        }
    }

    private void executeWrite(DurableWrite write, boolean terminal,
                              CompletableFuture<DurableWriteReceipt> receipt,
                              CompletableFuture<DurableWriteReceipt> runtimeAck) {
        try {
            DurableWriteReceipt committed = persist(write);
            receipt.complete(committed);
            try {
                failures.before(FailureStage.ACK, write);
                runtimeExecutor.execute(() -> runtimeAck.complete(committed));
            } catch (Exception exception) {
                runtimeAck.completeExceptionally(exception);
            }
        } catch (Throwable throwable) {
            receipt.completeExceptionally(throwable);
            runtimeAck.completeExceptionally(throwable);
        } finally {
            releaseCapacity(write, terminal);
        }
    }

    private DurableWriteReceipt persist(DurableWrite write) throws Exception {
        Path directory = root.resolve(write.namespace()).normalize();
        Path target = directory.resolve(write.recordId() + ".bin").normalize();
        requireInsideRoot(target);
        Files.createDirectories(directory);
        byte[] payload = write.payload();
        String checksum = checksum(payload);
        DurableWriteReceipt existingReceipt = validateExisting(write, target, payload, checksum);
        if (existingReceipt != null) return existingReceipt;
        byte[] encoded = encode(write);
        Path temporary = directory.resolve(write.recordId() + "." + write.operationId() + ".tmp");
        try {
            failures.before(FailureStage.WRITE, write);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) channel.write(buffer);
                failures.before(FailureStage.FORCE, write);
                channel.force(true);
            }
            failures.before(FailureStage.MOVE, write);
            synchronized (commitLock) {
                existingReceipt = validateExisting(write, target, payload, checksum);
                if (existingReceipt != null) return existingReceipt;
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    throw new IOException("atomic move is not supported for " + target, exception);
                }
                forceDirectory(directory);
                return new DurableWriteReceipt(write.operationId(), write.idempotencyKey(), write.recordVersion(),
                        checksum, target, clock.instant());
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private DurableWriteReceipt validateExisting(DurableWrite write, Path target, byte[] payload, String checksum)
            throws IOException {
        if (!Files.isRegularFile(target)) return null;
        StoredRecord existing = decode(Files.readAllBytes(target));
        if (existing.idempotencyKey().equals(write.idempotencyKey())) {
            if (existing.recordVersion() == write.recordVersion()
                    && MessageDigest.isEqual(existing.payload(), payload)) {
                return new DurableWriteReceipt(write.operationId(), write.idempotencyKey(), write.recordVersion(),
                        checksum, target, clock.instant());
            }
            throw new IOException("idempotency key conflicts with committed record");
        }
        if (write.recordVersion() <= existing.recordVersion()) throw new IOException("stale record version");
        return null;
    }

    @Override
    public CompletableFuture<Optional<DurableRecord>> read(String namespace, String recordId) {
        DurableWrite validation = new DurableWrite(UUID.randomUUID(), UUID.randomUUID(), namespace, recordId,
                "read-validation", 0, new byte[0]);
        CompletableFuture<Optional<DurableRecord>> result = new CompletableFuture<>();
        synchronized (submissionLock) {
            if (closed.get() || !normalPermits.tryAcquire()) {
                result.completeExceptionally(new RejectedExecutionException("repository queue is saturated or closed"));
                return result;
            }
            ReadTask task = new ReadTask(validation, namespace, recordId, result);
            try {
                worker.execute(task);
            } catch (RejectedExecutionException exception) {
                task.reject(exception);
            }
        }
        return result;
    }

    private void requireInsideRoot(Path path) {
        if (!path.startsWith(root)) throw new IllegalArgumentException("durable path leaves repository root");
    }

    private static void forceDirectory(Path directory) throws IOException {
        if ("\\".equals(directory.getFileSystem().getSeparator())) {
            // The Windows provider rejects directory channels instead of exposing directory fsync.
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Directory fsync is not supported by every filesystem/JVM combination.
        }
    }

    private static String checksum(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static byte[] encode(DurableWrite write) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(FILE_MAGIC);
            output.writeLong(write.recordVersion());
            output.writeUTF(write.idempotencyKey());
            byte[] payload = write.payload();
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();
            return bytes.toByteArray();
        }
    }

    private static StoredRecord decode(byte[] encoded) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != FILE_MAGIC) throw new IOException("invalid durable record magic");
            long version = input.readLong();
            String idempotencyKey = input.readUTF();
            int length = input.readInt();
            if (length < 0 || length > encoded.length) throw new IOException("invalid durable payload length");
            byte[] payload = input.readNBytes(length);
            if (payload.length != length || input.read() != -1) throw new IOException("truncated or trailing durable data");
            return new StoredRecord(version, idempotencyKey, payload);
        }
    }

    private static DurableSubmission rejected(CompletableFuture<DurableWriteReceipt> receipt,
                                              CompletableFuture<DurableWriteReceipt> runtimeAck, String detail) {
        RejectedExecutionException failure = new RejectedExecutionException(detail);
        receipt.completeExceptionally(failure);
        runtimeAck.completeExceptionally(failure);
        return new DurableSubmission(false, receipt, runtimeAck, detail);
    }

    private void releaseCapacity(DurableWrite write, boolean terminal) {
        if (terminal) {
            synchronized (submissionLock) {
                terminalInFlight.remove(write.instanceId());
            }
        } else normalPermits.release();
    }

    @Override
    public RepositoryDiagnostics diagnostics() {
        return new RepositoryDiagnostics(normalCapacity, normalCapacity - normalPermits.availablePermits(),
                worker.getQueue().size() + terminalWorker.getQueue().size(), terminalReservations.size(),
                terminalInFlight.size(), closed.get());
    }

    @Override
    public void close() {
        synchronized (submissionLock) {
            if (!closed.compareAndSet(false, true)) return;
            worker.shutdown();
            terminalWorker.shutdown();
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        settleStoppedTasks(worker, deadline);
        settleStoppedTasks(terminalWorker, deadline);
    }

    private void settleStoppedTasks(ThreadPoolExecutor executor, long deadline) {
        List<Runnable> stopped = List.of();
        try {
            long remaining = Math.max(0, deadline - System.nanoTime());
            if (!executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) stopped = executor.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stopped = executor.shutdownNow();
        }
        RejectedExecutionException failure = new RejectedExecutionException("repository closed before operation ran");
        for (Runnable task : stopped) {
            if (task instanceof RepositoryTask repositoryTask) repositoryTask.reject(failure);
            else if (task instanceof ReadTask readTask) readTask.reject(failure);
        }
    }

    private final class RepositoryTask implements Runnable {
        private final DurableWrite write;
        private final boolean terminal;
        private final CompletableFuture<DurableWriteReceipt> receipt;
        private final CompletableFuture<DurableWriteReceipt> runtimeAck;
        private final AtomicBoolean capacityReleased = new AtomicBoolean();

        private RepositoryTask(DurableWrite write, boolean terminal,
                               CompletableFuture<DurableWriteReceipt> receipt,
                               CompletableFuture<DurableWriteReceipt> runtimeAck) {
            this.write = write;
            this.terminal = terminal;
            this.receipt = receipt;
            this.runtimeAck = runtimeAck;
        }

        @Override
        public void run() {
            executeWrite(write, terminal, receipt, runtimeAck);
        }

        private void reject(Throwable failure) {
            receipt.completeExceptionally(failure);
            runtimeAck.completeExceptionally(failure);
            if (capacityReleased.compareAndSet(false, true)) releaseCapacity(write, terminal);
        }
    }

    private final class ReadTask implements Runnable {
        private final DurableWrite validation;
        private final String namespace;
        private final String recordId;
        private final CompletableFuture<Optional<DurableRecord>> result;
        private final AtomicBoolean permitReleased = new AtomicBoolean();

        private ReadTask(DurableWrite validation, String namespace, String recordId,
                         CompletableFuture<Optional<DurableRecord>> result) {
            this.validation = validation;
            this.namespace = namespace;
            this.recordId = recordId;
            this.result = result;
        }

        @Override
        public void run() {
            try {
                Path path = root.resolve(validation.namespace()).resolve(validation.recordId() + ".bin").normalize();
                requireInsideRoot(path);
                if (!Files.isRegularFile(path)) result.complete(Optional.empty());
                else {
                    StoredRecord stored = decode(Files.readAllBytes(path));
                    result.complete(Optional.of(new DurableRecord(namespace, recordId, stored.payload(),
                            checksum(stored.payload()), path)));
                }
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            } finally {
                releasePermit();
            }
        }

        private void reject(Throwable failure) {
            result.completeExceptionally(failure);
            releasePermit();
        }

        private void releasePermit() {
            if (permitReleased.compareAndSet(false, true)) normalPermits.release();
        }
    }

    enum FailureStage { SUBMIT, WRITE, FORCE, MOVE, ACK }

    @FunctionalInterface
    interface FailureInjector {
        void before(FailureStage stage, DurableWrite write) throws Exception;

        static FailureInjector none() {
            return (stage, write) -> { };
        }
    }

    private record StoredRecord(long recordVersion, String idempotencyKey, byte[] payload) { }
}
