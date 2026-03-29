package com.sasoori.util;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Reader;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thin Gson wrapper shared across the application.
 */
public final class JsonUtil {

    public static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .registerTypeAdapter(OffsetDateTime.class, new TypeAdapter<OffsetDateTime>() {
                @Override
                public void write(JsonWriter out, OffsetDateTime value) throws IOException {
                    if (value == null) { out.nullValue(); return; }
                    out.value(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                }
                @Override
                public OffsetDateTime read(JsonReader in) throws IOException {
                    if (in.peek() == com.google.gson.stream.JsonToken.NULL) { in.nextNull(); return null; }
                    return OffsetDateTime.parse(in.nextString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                }
            })
            .create();

    private JsonUtil() {}

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    public static <T> T fromJson(Reader reader, Class<T> type) {
        return GSON.fromJson(reader, type);
    }

    public static JsonObject parseObject(String json) {
        return GSON.fromJson(json, JsonObject.class);
    }
}
