package com.kltyton.autoseamblend.compat.ctm_mod.authoring.property;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.property.NativePropertyCompanionCollector;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 中文：解析 NeoForge 独占 CTM Mod 的 blockstate 主文档与模型引用。
 * English: Resolves the principal blockstate and model references for NeoForge-only CTM Mod.
 */
public final class CtmModNativePropertyCompanions {
    private CtmModNativePropertyCompanions() {}

    public static ManagedAuthoringFile principal(List<ManagedAuthoringFile> documents) {
        return List.copyOf(Objects.requireNonNull(documents, "documents")).stream()
                .filter(document -> document.relativePath().contains("/blockstates/")
                        && document.relativePath().endsWith(".json"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "NATIVE_PROPERTY_DOCUMENT_MISSING"));
    }

    public static Map<String, byte[]> collect(
            String sourcePath,
            byte[] source,
            List<ManagedAuthoringFile> templateDocuments,
            String templatePrincipalPath,
            NativePropertyCompanionCollector.DocumentReader reader) throws IOException {
        return NativePropertyCompanionCollector.collectModelReferenced(
                sourcePath,
                source,
                templateDocuments,
                templatePrincipalPath,
                reader,
                CtmModNativePropertyCompanions::collectModelReferences);
    }

    private static void collectModelReferences(
            JsonObject root,
            Consumer<String> addReference) {
        collectModelReferences((JsonElement) root, addReference);
    }

    private static void collectModelReferences(
            JsonElement encoded,
            Consumer<String> addReference) {
        if (encoded == null) {
            return;
        }
        if (encoded.isJsonArray()) {
            encoded.getAsJsonArray().forEach(value ->
                    collectModelReferences(value, addReference));
            return;
        }
        if (!encoded.isJsonObject()) {
            return;
        }
        JsonObject object = encoded.getAsJsonObject();
        if ("ctm:connected_texture_model".equals(string(object.get("type")))) {
            String model = string(object.get("model_location"));
            if (model != null && !model.isBlank()) {
                addReference.accept(model);
            }
        }
        object.entrySet().forEach(entry ->
                collectModelReferences(entry.getValue(), addReference));
    }

    private static String string(JsonElement value) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }
}
