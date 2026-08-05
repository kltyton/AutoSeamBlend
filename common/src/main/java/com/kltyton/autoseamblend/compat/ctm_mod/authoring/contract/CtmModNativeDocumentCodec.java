package com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import net.minecraft.resources.Identifier;

/**
 * 中文：CTM Mod 原生 JSON 的 lossless 执行视图与 baked 视图转换；不依赖 NeoForge 或 CTM 实现类。
 *
 * <p>English: Converts CTM Mod native JSON into lossless execution and baked views without
 * depending on NeoForge or CTM implementation classes.</p>
 */
public final class CtmModNativeDocumentCodec {
    private CtmModNativeDocumentCodec() {}

    /**
     * 中文：把已解析的 AutoSeamBlend method 写入 CTM 原生 variant.kind。
     * English: Writes the resolved AutoSeamBlend method into the CTM native variant.kind.
     */
    public static byte[] nativeExecutionView(
            byte[] blockstate,
            ConnectionMethod method) throws IOException {
        Objects.requireNonNull(blockstate, "blockstate");
        Objects.requireNonNull(method, "method");
        if (method == ConnectionMethod.AUTO) {
            throw new IOException("CTM_MOD_AUTO_METHOD_UNRESOLVED");
        }
        if (method == ConnectionMethod.NONE || method == ConnectionMethod.FIXED) {
            return blockstate.clone();
        }
        JsonElement parsed = parse(blockstate);
        int patched = patchNativeKinds(parsed, CtmModCarrierLayout.forMethod(method).kind());
        if (patched == 0) {
            throw new IOException("CTM_MOD_CONNECTED_MODEL_MISSING");
        }
        return (parsed + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 中文：删除项目扩展但保留所有 CTM 原生字段。
     * English: Removes project extensions while retaining every CTM native field.
     */
    public static byte[] stripAuthoringExtension(byte[] blockstate) throws IOException {
        JsonElement parsed = parse(blockstate);
        if (!(parsed instanceof JsonObject root)) {
            throw new IOException("CTM_MOD_DOCUMENT_JSON_INVALID");
        }
        root.remove("autoseamblend");
        return (root + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /** 中文：返回项目扩展字段的 authoring 值。 / English: Returns authoring values for project extensions. */
    public static Map<String, Optional<String>> authoringExtension(
            ConnectionMethod requestedMethod,
            boolean compatibility) {
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        return Map.of(
                "method", Optional.of(new JsonPrimitive(requestedMethod.serializedName()).toString()),
                "compatibility", Optional.of(Boolean.toString(compatibility)));
    }

    /** 中文：返回 baked 导出中需要移除的项目字段。 / English: Returns project fields removed for baked export. */
    public static Map<String, Optional<String>> bakedExtension() {
        return Map.of(
                "id", Optional.empty(),
                "selector", Optional.empty(),
                "method", Optional.empty(),
                "compatibility", Optional.empty());
    }

    /**
     * 中文：收集 CTM 原生 blockstate 递归引用的模型路径。
     * English: Collects model paths recursively referenced by the CTM native blockstate.
     */
    public static Set<String> referencedModels(byte[] blockstate) throws IOException {
        JsonElement parsed = parse(blockstate);
        LinkedHashSet<String> modelIds = new LinkedHashSet<>();
        collectReferencedModels(parsed, modelIds);
        if (modelIds.isEmpty()) {
            throw new IOException("CTM_MOD_CONNECTED_MODEL_MISSING");
        }
        return Set.copyOf(modelIds);
    }

    public static String modelId(String modelPath) throws IOException {
        Objects.requireNonNull(modelPath, "modelPath");
        String[] segments = modelPath.split("/", 5);
        if (segments.length != 5
                || !"assets".equals(segments[0])
                || !"models".equals(segments[2])
                || !segments[4].endsWith(".json")) {
            throw new IOException("CTM_MOD_MODEL_PATH_INVALID:" + modelPath);
        }
        return segments[1] + ':' + segments[3] + '/'
                + segments[4].substring(0, segments[4].length() - ".json".length());
    }

    public static String modelPath(String modelId) throws IOException {
        Identifier parsed = Identifier.tryParse(Objects.requireNonNull(modelId, "modelId"));
        if (parsed == null) {
            throw new IOException("CTM_MOD_MODEL_ID_INVALID:" + modelId);
        }
        return "assets/" + parsed.getNamespace() + "/models/"
                + parsed.getPath() + ".json";
    }

    private static JsonElement parse(byte[] blockstate) throws IOException {
        try {
            return JsonParser.parseString(
                    StandardCharsets.UTF_8.newDecoder()
                            .decode(java.nio.ByteBuffer.wrap(blockstate))
                            .toString());
        } catch (java.nio.charset.CharacterCodingException | RuntimeException exception) {
            throw new IOException("CTM_MOD_DOCUMENT_JSON_INVALID", exception);
        }
    }

    private static int patchNativeKinds(JsonElement value, String kind) throws IOException {
        if (value == null) {
            return 0;
        }
        if (value.isJsonArray()) {
            int patched = 0;
            for (JsonElement entry : value.getAsJsonArray()) {
                patched += patchNativeKinds(entry, kind);
            }
            return patched;
        }
        if (!(value instanceof JsonObject object)) {
            return 0;
        }
        if (CtmModNativeDocument.MODEL_TYPE.equals(string(object.get("type")))) {
            if (!(object.get("variant") instanceof JsonObject variant)) {
                throw new IOException("CTM_MOD_CONNECTED_MODEL_VARIANT_MISSING");
            }
            variant.addProperty("kind", kind);
            return 1;
        }
        int patched = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            patched += patchNativeKinds(entry.getValue(), kind);
        }
        return patched;
    }

    private static void collectReferencedModels(JsonElement value, Set<String> output) {
        if (value == null) {
            return;
        }
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(entry -> collectReferencedModels(entry, output));
            return;
        }
        if (!(value instanceof JsonObject object)) {
            return;
        }
        if (CtmModNativeDocument.MODEL_TYPE.equals(string(object.get("type")))
                && string(object.get("model_location")) != null) {
            output.add(string(object.get("model_location")));
            return;
        }
        object.entrySet().forEach(entry -> collectReferencedModels(entry.getValue(), output));
    }

    private static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }
}
