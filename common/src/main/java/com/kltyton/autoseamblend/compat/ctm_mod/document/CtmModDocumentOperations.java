package com.kltyton.autoseamblend.compat.ctm_mod.document;

import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.authoring.document.json.LosslessJsonPatch;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 中文：把 NeoForge 独占 CTM Mod 格式的文档合并与属性补丁接入公共
 * NativeDocumentOperations 家族注册点。
 *
 * English: Connects NeoForge-only CTM Mod document merging and property
 * patching to the shared NativeDocumentOperations family registry.
 */
public enum CtmModDocumentOperations
        implements NativeDocumentOperations.FamilyOperations {
    INSTANCE;

    private static final Set<String> CTM_MOD_KEYS = Set.of(
            "id", "selector", "method", "compatibility");

    @Override
    public byte[] mergeSource(
            EngineFamily family,
            String path,
            byte[] existing,
            byte[] desired) throws IOException {
        requireCtmMod(family);
        Objects.requireNonNull(path, "path");
        if (!path.endsWith(".json") && !path.endsWith(".mcmeta")) {
            return Objects.requireNonNull(desired, "desired").clone();
        }
        String merged = LosslessJsonPatch.replaceNestedKeys(
                decode(existing, "NATIVE_DOCUMENT_JSON_INVALID"),
                "NATIVE_DOCUMENT_JSON_INVALID",
                decode(desired, "NATIVE_TEMPLATE_JSON_INVALID"),
                "NATIVE_TEMPLATE_JSON_INVALID",
                "autoseamblend",
                true,
                "id",
                "selector",
                "method",
                "compatibility");
        return merged.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] resolveProperty(
            EngineFamily family,
            String path,
            byte[] source,
            Map<String, Optional<String>> values) throws IOException {
        requireCtmMod(family);
        String resolvedPath = Objects.requireNonNull(path, "path");
        byte[] resolvedSource = Objects.requireNonNull(source, "source").clone();
        Map<String, Optional<String>> requested = Objects.requireNonNull(values, "values");
        ArrayList<String> keys = new ArrayList<>(requested.keySet());
        keys.forEach(key -> Objects.requireNonNull(key, "property key"));
        keys.sort(String::compareTo);
        LinkedHashMap<String, Optional<String>> ordered = new LinkedHashMap<>();
        keys.forEach(key -> ordered.put(
                key,
                Objects.requireNonNull(requested.get(key), "property value")));
        Map<String, Optional<String>> resolvedValues = Collections.unmodifiableMap(ordered);
        if (resolvedValues.isEmpty()) {
            return resolvedSource;
        }
        if (!resolvedPath.endsWith(".json")
                || !resolvedPath.contains("/blockstates/")
                || !CTM_MOD_KEYS.containsAll(resolvedValues.keySet())) {
            throw new IOException("NATIVE_PROPERTY_PATCH_UNSUPPORTED");
        }
        String invalidCode = "CTM_MOD_DOCUMENT_JSON_INVALID";
        String resolved = LosslessJsonPatch.patchNestedValues(
                decode(resolvedSource, invalidCode),
                invalidCode,
                "autoseamblend",
                true,
                resolvedValues,
                "NATIVE_PROPERTY_JSON_INVALID");
        return resolved.getBytes(StandardCharsets.UTF_8);
    }

    private static void requireCtmMod(EngineFamily family) {
        if (family != EngineFamily.CTM_MOD) {
            throw new IllegalArgumentException(
                    "CTM_MOD_DOCUMENT_OPERATIONS_REQUIRED");
        }
    }

    private static String decode(byte[] source, String error) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(Objects.requireNonNull(source, "source")))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException(error, exception);
        }
    }
}
