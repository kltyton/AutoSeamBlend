package com.kltyton.autoseamblend.authoring.format.fusion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：保留未知 Fusion JSON 字段的不可变原生文档快照。
 *
 * <p>English: Immutable native-document snapshot that retains unknown Fusion JSON fields.
 */
public final class FusionNativeDocument {
    public static final String ID_KEY = "id";
    public static final String METHOD_KEY = "method";
    public static final String COMPATIBILITY_KEY = "compatibility";

    private final String encoded;

    private FusionNativeDocument(JsonObject document) {
        encoded = Objects.requireNonNull(document, "document").deepCopy().toString();
    }

    public static FusionNativeDocument parse(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            return new FusionNativeDocument(JsonParser.parseString(encoded).getAsJsonObject());
        } catch (RuntimeException exception) {
            throw new JsonParseException("Fusion metadata must be a JSON object", exception);
        }
    }

    public static FusionNativeDocument of(JsonObject document) {
        return new FusionNativeDocument(document);
    }

    public JsonObject json() {
        return JsonParser.parseString(encoded).getAsJsonObject();
    }

    public Optional<ConnectionMethod> requestedMethod() {
        JsonObject document = json();
        if (!document.has(METHOD_KEY)) {
            return Optional.empty();
        }
        if (!document.get(METHOD_KEY).isJsonPrimitive()
                || !document.getAsJsonPrimitive(METHOD_KEY).isString()) {
            throw new JsonParseException("Fusion method extension must be a string");
        }
        String serialized = document.get(METHOD_KEY).getAsString();
        return Optional.of(ConnectionMethod.parse(serialized).orElseThrow(
                () -> new JsonParseException("Unknown AutoSeamBlend method: " + serialized)));
    }

    public Optional<Boolean> compatibility() {
        JsonObject document = json();
        if (!document.has(COMPATIBILITY_KEY)) {
            return Optional.empty();
        }
        if (!document.get(COMPATIBILITY_KEY).isJsonPrimitive()
                || !document.getAsJsonPrimitive(COMPATIBILITY_KEY).isBoolean()) {
            throw new JsonParseException("Fusion compatibility extension must be a boolean");
        }
        return Optional.of(document.get(COMPATIBILITY_KEY).getAsBoolean());
    }

    public Optional<String> entryId() {
        JsonObject document = json();
        if (!document.has(ID_KEY)) {
            return Optional.empty();
        }
        if (!document.get(ID_KEY).isJsonPrimitive()
                || !document.getAsJsonPrimitive(ID_KEY).isString()) {
            throw new JsonParseException("Fusion id must be a string");
        }
        return Optional.of(document.get(ID_KEY).getAsString());
    }

    public FusionNativeDocument withEntryId(Optional<String> entryId) {
        JsonObject updated = json();
        Objects.requireNonNull(entryId, "entryId").ifPresentOrElse(
                value -> updated.addProperty(ID_KEY, value), () -> updated.remove(ID_KEY));
        return new FusionNativeDocument(updated);
    }

    /**
     * 中文：创建交给 Fusion 原生 serializer 的视图，不改变保留的 authoring 文档。
     *
     * <p>English: Creates the view passed to Fusion's native serializer without mutating the
     * retained authoring document.
     */
    public JsonObject nativeExecutionJson() {
        JsonObject execution = json();
        execution.remove(METHOD_KEY);
        execution.remove(COMPATIBILITY_KEY);
        return execution;
    }

    public FusionNativeDocument withExtensions(
            ConnectionMethod method,
            boolean compatibility) {
        JsonObject updated = json();
        updated.addProperty(METHOD_KEY, Objects.requireNonNull(method, "method").serializedName());
        updated.addProperty(COMPATIBILITY_KEY, compatibility);
        return new FusionNativeDocument(updated);
    }

    /** 中文：仅删除项目扩展；这不是完整 baked 导出。 / English: Removes only project extensions; this is not a complete baked export. */
    public FusionNativeDocument withoutProjectExtensions() {
        return new FusionNativeDocument(nativeExecutionJson());
    }

    public String encode() {
        return encoded;
    }
}
