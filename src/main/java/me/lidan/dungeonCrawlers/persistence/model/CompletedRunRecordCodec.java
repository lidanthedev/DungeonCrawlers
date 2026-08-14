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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;

public final class CompletedRunRecordCodec {
    private static final int SCHEMA_VERSION = 1;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .registerTypeAdapter(byte[].class, new ByteArrayAdapter()).create();

    public byte[] encode(CompletedRunRecord record) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("schema-version", SCHEMA_VERSION);
        envelope.add("record", gson.toJsonTree(record));
        JsonElement canonical = canonicalize(envelope);
        return gson.toJson(canonical).getBytes(StandardCharsets.UTF_8);
    }

    public CompletedRunRecord decode(byte[] payload) {
        JsonElement parsed = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new JsonParseException("completed run payload must be an object");
        JsonObject envelope = parsed.getAsJsonObject();
        JsonElement version = envelope.get("schema-version");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("completed run schema-version is missing");
        }
        int parsedVersion;
        try {
            parsedVersion = version.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw new JsonParseException("invalid completed run schema-version", exception);
        }
        if (parsedVersion != SCHEMA_VERSION) {
            throw new JsonParseException("unsupported completed run schema-version " + parsedVersion);
        }
        JsonElement encodedRecord = envelope.get("record");
        if (encodedRecord == null) throw new JsonParseException("completed run record is missing");
        CompletedRunRecord record;
        try {
            record = gson.fromJson(encodedRecord, CompletedRunRecord.class);
        } catch (JsonParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JsonParseException("invalid completed run record", exception);
        }
        if (record == null) throw new JsonParseException("completed run record is null");
        return record;
    }

    private static JsonElement canonicalize(JsonElement value) {
        if (value.isJsonArray()) {
            var result = new com.google.gson.JsonArray();
            value.getAsJsonArray().forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        if (!value.isJsonObject()) return value.deepCopy();
        JsonObject result = new JsonObject();
        ArrayList<Map.Entry<String, JsonElement>> entries = new ArrayList<>(value.getAsJsonObject().entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        entries.forEach(entry -> result.add(entry.getKey(), canonicalize(entry.getValue())));
        return result;
    }

    private static final class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant source, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(source.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {
            try { return Instant.parse(json.getAsString()); }
            catch (RuntimeException exception) { throw new JsonParseException("invalid instant", exception); }
        }
    }

    private static final class ByteArrayAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
        @Override
        public JsonElement serialize(byte[] source, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(Base64.getEncoder().encodeToString(source));
        }

        @Override
        public byte[] deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                return Base64.getDecoder().decode(json.getAsString());
            } catch (RuntimeException exception) {
                throw new JsonParseException("invalid Base64 item payload", exception);
            }
        }
    }
}
