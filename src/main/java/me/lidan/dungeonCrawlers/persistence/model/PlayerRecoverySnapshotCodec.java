package me.lidan.dungeonCrawlers.persistence.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class PlayerRecoverySnapshotCodec {
    private static final int SCHEMA_VERSION = 1;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping()
            .registerTypeAdapter(Instant.class, new InstantAdapter()).create();

    public byte[] encode(PlayerRecoverySnapshot snapshot) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("schema-version", SCHEMA_VERSION);
        envelope.add("snapshot", gson.toJsonTree(snapshot));
        return gson.toJson(envelope).getBytes(StandardCharsets.UTF_8);
    }

    public PlayerRecoverySnapshot decode(byte[] payload) {
        try {
            JsonElement parsed = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new JsonParseException("player snapshot must be an object");
            JsonObject envelope = parsed.getAsJsonObject();
            JsonElement version = envelope.get("schema-version");
            if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException("player snapshot schema-version is missing");
            }
            int parsedVersion = version.getAsBigDecimal().intValueExact();
            if (parsedVersion != SCHEMA_VERSION) {
                throw new JsonParseException("unsupported player snapshot schema-version " + parsedVersion);
            }
            JsonElement encoded = envelope.get("snapshot");
            if (encoded == null || encoded.isJsonNull() || !encoded.isJsonObject()) {
                throw new JsonParseException("player snapshot is missing or not an object");
            }
            JsonObject snapshot = encoded.getAsJsonObject();
            for (String field : new String[]{"playerId", "instanceId", "world", "x", "y", "z", "yaw", "pitch",
                    "gameMode", "health", "foodLevel", "saturation", "exhaustion", "fireTicks",
                    "remainingAir", "maximumAir", "capturedAt"}) {
                if (!snapshot.has(field) || snapshot.get(field).isJsonNull()) {
                    throw new JsonParseException("player snapshot field is missing: " + field);
                }
            }
            PlayerRecoverySnapshot result = gson.fromJson(encoded, PlayerRecoverySnapshot.class);
            if (result == null) throw new JsonParseException("player snapshot is null");
            return result;
        } catch (JsonParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JsonParseException("invalid player snapshot", exception);
        }
    }

    private static final class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant source, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(source.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            try { return Instant.parse(json.getAsString()); }
            catch (RuntimeException exception) { throw new JsonParseException("invalid snapshot instant", exception); }
        }
    }
}
