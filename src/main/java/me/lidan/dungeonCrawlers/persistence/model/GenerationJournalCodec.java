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

public final class GenerationJournalCodec {
    private static final int SCHEMA_VERSION = 1;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping()
            .registerTypeAdapter(Instant.class, new InstantAdapter()).create();

    public byte[] encode(GenerationJournal journal) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("schema-version", SCHEMA_VERSION);
        envelope.add("journal", gson.toJsonTree(journal));
        return gson.toJson(envelope).getBytes(StandardCharsets.UTF_8);
    }

    public GenerationJournal decode(byte[] payload) {
        try {
            JsonElement parsed = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new JsonParseException("generation journal must be an object");
            JsonObject envelope = parsed.getAsJsonObject();
            JsonElement version = envelope.get("schema-version");
            if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
                throw new JsonParseException("generation journal schema-version is missing");
            }
            int parsedVersion = version.getAsBigDecimal().intValueExact();
            if (parsedVersion != SCHEMA_VERSION) {
                throw new JsonParseException("unsupported generation journal schema-version " + parsedVersion);
            }
            JsonElement encoded = envelope.get("journal");
            if (encoded == null) throw new JsonParseException("generation journal is missing");
            if (!encoded.isJsonObject()) throw new JsonParseException("generation journal must be an object");
            JsonObject journalObject = encoded.getAsJsonObject();
            for (String field : new String[]{"instanceId", "seed", "slotId", "world", "plannedBounds",
                    "participants", "configHash", "contentHash", "algorithmVersion", "status", "createdAt"}) {
                if (!journalObject.has(field) || journalObject.get(field).isJsonNull()) {
                    throw new JsonParseException("generation journal field is missing: " + field);
                }
            }
            GenerationJournal journal = gson.fromJson(encoded, GenerationJournal.class);
            if (journal == null) throw new JsonParseException("generation journal is null");
            return journal;
        } catch (JsonParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JsonParseException("invalid generation journal", exception);
        }
    }

    private static final class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant source, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(source.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            try {
                return Instant.parse(json.getAsString());
            } catch (RuntimeException exception) {
                throw new JsonParseException("invalid generation journal instant", exception);
            }
        }
    }
}
