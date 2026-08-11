package com.kltyton.autoseamblend.compat.ctm_mod.authoring.property;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocument;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocument.AthenaConnection;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocument.SelectorPresence;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorResolver;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 中文：把 NeoForge 独占 CTM Mod blockstate 投影为公共属性值对象。
 * English: Projects a NeoForge-only CTM Mod blockstate into the shared property value object.
 */
public final class CtmModNativePropertyDocumentParser {
    private CtmModNativePropertyDocumentParser() {}

    public static NativePropertyDocument parse(
            Optional<String> receiver,
            String documentPath,
            String sourcePath,
            byte[] source,
            Map<String, byte[]> companions,
            ConnectionMethod fallbackMethod,
            boolean fallbackCompatibility,
            NativeBlockSelectorResolver selectorResolver) throws IOException {
        JsonObject root = json(source);
        JsonObject extension = root.has("autoseamblend")
                        && root.get("autoseamblend").isJsonObject()
                ? root.getAsJsonObject("autoseamblend")
                : new JsonObject();
        List<String> matching = stringsOrSingle(extension.get("selector"));
        List<String> connecting = connectionBlocks(root);
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("format", "CTM Lib blockstate JSON");
        details.put("selector", primitive(extension.get("selector")));
        details.put("id", Objects.toString(string(extension.get("id")), ""));
        return NativePropertyDocument.createReadOnly(
                EngineFamily.CTM_MOD,
                receiver,
                documentPath,
                sourcePath,
                source,
                companions,
                selectorResolver,
                string(extension.get("id")),
                matching,
                matching.isEmpty()
                        ? SelectorPresence.ABSENT
                        : SelectorPresence.PRESENT_VALUES,
                details,
                AthenaConnection.CUSTOM,
                connecting,
                connecting.isEmpty()
                        ? SelectorPresence.ABSENT
                        : SelectorPresence.PRESENT_VALUES,
                ConnectionMethod.parse(string(extension.get("method")))
                        .orElse(fallbackMethod),
                booleanValue(extension.get("compatibility"), fallbackCompatibility));
    }

    private static List<String> connectionBlocks(JsonObject root) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        collectConnectionBlocks(root, result);
        return List.copyOf(result);
    }

    private static void collectConnectionBlocks(JsonElement encoded, Set<String> output) {
        if (encoded == null) {
            return;
        }
        if (encoded.isJsonArray()) {
            encoded.getAsJsonArray().forEach(value -> collectConnectionBlocks(value, output));
            return;
        }
        if (!encoded.isJsonObject()) {
            return;
        }
        JsonObject object = encoded.getAsJsonObject();
        if ("ctm:connected_texture_model".equals(string(object.get("type")))
                && object.get("variant") instanceof JsonObject variant) {
            String block = string(variant.get("block"));
            if (block != null && !block.isBlank()) {
                output.add(block);
            }
        }
        object.entrySet().forEach(entry -> collectConnectionBlocks(entry.getValue(), output));
    }

    private static List<String> stringsOrSingle(JsonElement value) {
        String single = string(value);
        if (single != null) {
            return List.of(single);
        }
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>();
        value.getAsJsonArray().forEach(element -> {
            String candidate = string(element);
            if (candidate != null && !candidate.isBlank()) {
                result.add(candidate);
            }
        });
        return List.copyOf(result);
    }

    private static boolean booleanValue(JsonElement value, boolean fallback) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isBoolean()
                ? value.getAsBoolean()
                : fallback;
    }

    private static String primitive(JsonElement value) {
        return value == null ? "" : value.toString();
    }

    private static String string(JsonElement value) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static JsonObject json(byte[] source) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(Objects.requireNonNull(source, "source"), StandardCharsets.UTF_8));
            if (parsed instanceof JsonObject root) {
                return root;
            }
        } catch (RuntimeException exception) {
            throw new IOException("NATIVE_PROPERTY_JSON_INVALID", exception);
        }
        throw new IOException("NATIVE_PROPERTY_JSON_INVALID");
    }
}
