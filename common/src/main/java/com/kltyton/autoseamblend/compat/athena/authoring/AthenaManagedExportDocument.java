package com.kltyton.autoseamblend.compat.athena.authoring;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.compat.athena.document.AthenaDocumentExtensions;
import com.kltyton.autoseamblend.export.io.CanonicalJson;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：保留 Athena authoring 原字节，并生成移除扩展后的本家族 baked JSON。
 * English: Preserves Athena authoring bytes and produces same-family baked JSON with extensions removed.
 */
public record AthenaManagedExportDocument(byte[] authoring, byte[] baked) {
    public AthenaManagedExportDocument {
        authoring = Objects.requireNonNull(authoring, "authoring").clone();
        baked = Objects.requireNonNull(baked, "baked").clone();
    }

    public static AthenaManagedExportDocument create(
            byte[] authoring,
            ConnectionMethod resolvedMethod) {
        return create(authoring, resolvedMethod, null);
    }

    public static AthenaManagedExportDocument create(
            byte[] authoring,
            ConnectionMethod resolvedMethod,
            String bakedTexturePattern) {
        return create(authoring, resolvedMethod, bakedTexturePattern, Map.of());
    }

    public static AthenaManagedExportDocument create(
            byte[] authoring,
            ConnectionMethod resolvedMethod,
            String bakedTexturePattern,
            Map<AthenaPaneTilePlan.Role, String> paneTextures) {
        Objects.requireNonNull(authoring, "authoring");
        JsonElement parsed = JsonParser.parseString(new String(
                authoring,
                StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Athena document root must be an object");
        }
        JsonObject execution = AthenaDocumentExtensions.executionView(
                parsed.getAsJsonObject(),
                resolvedMethod);
        rewriteBakedTextures(
                execution,
                resolvedMethod,
                bakedTexturePattern,
                Map.copyOf(Objects.requireNonNull(paneTextures, "paneTextures")));
        byte[] baked = CanonicalJson.stringify(toValue(execution))
                .getBytes(StandardCharsets.UTF_8);
        return new AthenaManagedExportDocument(authoring, baked);
    }

    /**
     * 中文：原生槽无需补全时只移除项目扩展，不改写作者纹理声明。
     * English: Removes project extensions without rewriting author texture declarations when native slots need no completion.
     */
    public static AthenaManagedExportDocument preserve(
            byte[] authoring,
            ConnectionMethod resolvedMethod) {
        Objects.requireNonNull(authoring, "authoring");
        JsonElement parsed = JsonParser.parseString(new String(authoring, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Athena document root must be an object");
        }
        JsonObject execution = AthenaDocumentExtensions.executionView(
                parsed.getAsJsonObject(),
                resolvedMethod);
        return new AthenaManagedExportDocument(
                authoring,
                CanonicalJson.stringify(toValue(execution)).getBytes(StandardCharsets.UTF_8));
    }

    private static void rewriteBakedTextures(
            JsonObject execution,
            ConnectionMethod method,
            String pattern,
            Map<AthenaPaneTilePlan.Role, String> paneTextures) {
        JsonElement loader = execution.get(AthenaDocumentExtensions.LOADER);
        boolean pane = loader != null
                && loader.isJsonPrimitive()
                && AthenaDocumentExtensions.PANE_LOADER.equals(loader.getAsString());
        if (method == ConnectionMethod.NONE) {
            return;
        }
        if (pane) {
            if (paneTextures.size() != AthenaPaneTilePlan.Role.values().length) {
                throw new IllegalArgumentException(
                        "Athena pane baked export requires all seven native material roles");
            }
            JsonObject nativeTextures = new JsonObject();
            for (AthenaPaneTilePlan.Role role : AthenaPaneTilePlan.Role.values()) {
                String texture = paneTextures.get(role);
                if (texture == null || texture.isBlank() || texture.contains("[$index]")) {
                    throw new IllegalArgumentException(
                            "Athena pane texture must be a concrete native texture id: " + role);
                }
                nativeTextures.addProperty(role.jsonKey(), texture);
            }
            execution.add("ctm_textures", nativeTextures);
            return;
        }
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException(
                    "Athena baked export requires an explicit native texture path");
        }
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before baked export");
        }
        if (method == ConnectionMethod.TOP || method == ConnectionMethod.FIXED) {
            if (pattern.contains("[$index]")) {
                throw new IllegalArgumentException(
                        "Athena single-slot baked texture path must be concrete");
            }
            execution.addProperty("ctm_textures", pattern);
            return;
        }
        if (!pattern.contains("[$index]")) {
            throw new IllegalArgumentException(
                    "Athena 47-slice baked texture path must contain [$index]");
        }
        execution.addProperty("ctm_textures", pattern);
    }

    @Override
    public byte[] authoring() {
        return authoring.clone();
    }

    @Override
    public byte[] baked() {
        return baked.clone();
    }

    private static Object toValue(JsonElement value) {
        if (value.isJsonNull()) {
            return null;
        }
        if (value.isJsonObject()) {
            Map<String, Object> object = new LinkedHashMap<>();
            value.getAsJsonObject().entrySet().forEach(entry ->
                    object.put(entry.getKey(), toValue(entry.getValue())));
            return object;
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            List<Object> values = new ArrayList<>(array.size());
            array.forEach(element -> values.add(toValue(element)));
            return values;
        }
        if (value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        if (value.getAsJsonPrimitive().isNumber()) {
            return value.getAsNumber();
        }
        return value.getAsString();
    }
}
