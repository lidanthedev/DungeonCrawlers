package me.lidan.dungeonCrawlers.persistence.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public final class CompletedRunRecordCodec {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping()
            .registerTypeAdapter(Instant.class, new InstantAdapter()).create();

    public byte[] encode(CompletedRunRecord record) {
        JsonElement canonical = canonicalize(gson.toJsonTree(record));
        return gson.toJson(canonical).getBytes(StandardCharsets.UTF_8);
    }

    public CompletedRunRecord decode(byte[] payload) {
        CompletedRunRecord record = gson.fromJson(new String(payload, StandardCharsets.UTF_8), CompletedRunRecord.class);
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
}
