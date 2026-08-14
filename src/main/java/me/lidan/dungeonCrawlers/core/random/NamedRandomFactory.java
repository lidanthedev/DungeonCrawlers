package me.lidan.dungeonCrawlers.core.random;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.SplittableRandom;

public final class NamedRandomFactory {
    private final long instanceSeed;

    public NamedRandomFactory(long instanceSeed) {
        this.instanceSeed = instanceSeed;
    }

    public SplittableRandom stream(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("stream name must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(instanceSeed).array());
            digest.update((byte) 0);
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            return new SplittableRandom(ByteBuffer.wrap(digest.digest()).getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
