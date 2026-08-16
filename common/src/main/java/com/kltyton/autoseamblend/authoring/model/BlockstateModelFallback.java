package com.kltyton.autoseamblend.authoring.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：当启发式 {@code namespace:block/<block>} 模型没有解析出源纹理键时，从
 * blockstate 的 variants/multipart 模型引用中按确定顺序回退，选择第一个能解析出
 * 非空源纹理键的具体模型。
 *
 * <p>English: When the heuristic {@code namespace:block/<block>} model resolves
 * no source texture keys, falls back through the blockstate variants/multipart
 * model references in deterministic order and selects the first concrete model
 * that resolves nonempty source texture keys.
 */
public final class BlockstateModelFallback {
    private BlockstateModelFallback() {}

    /**
     * 中文：尝试为给定目标方块选择回退模型；缺失或损坏的可选文档/候选被安全跳过。
     *
     * English: Attempts to select a fallback model for the given target block;
     * missing or malformed optional documents/candidates are skipped safely.
     */
    public static Optional<Selected> select(
            ResourceManager resources,
            String targetBlockId,
            String sourceTextureId) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(targetBlockId, "targetBlockId");
        Objects.requireNonNull(sourceTextureId, "sourceTextureId");
        Identifier target =
                Identifier.tryParse(targetBlockId);
        if (target == null) {
            return Optional.empty();
        }
        Identifier blockstateId = Identifier.fromNamespaceAndPath(
                target.getNamespace(),
                "blockstates/"
                        + target.getPath()
                        + ".json");
        Optional<byte[]> encoded = read(
                resources,
                blockstateId);
        if (encoded.isEmpty()) {
            return Optional.empty();
        }
        Optional<JsonObject> root =
                parse(encoded.orElseThrow());
        if (root.isEmpty()) {
            return Optional.empty();
        }
        for (String reference :
                modelReferences(root.orElseThrow())) {
            String modelId = normalize(
                    reference,
                    target.getNamespace());
            if (modelId == null) {
                continue;
            }
            List<String> keys;
            try {
                keys = MinecraftModelTextureBindings.resolve(
                        resources,
                        modelId,
                        sourceTextureId);
            } catch (RuntimeException exception) {
                continue;
            }
            if (!keys.isEmpty()) {
                return Optional.of(
                        new Selected(modelId, keys));
            }
        }
        return Optional.empty();
    }

    /**
     * 中文：按文档出现顺序收集 variants 与 multipart apply 的模型引用并去重。
     *
     * English: Collects variants and multipart apply model references in
     * document order with dedupe.
     */
    private static List<String> modelReferences(
            JsonObject root) {
        LinkedHashSet<String> references =
                new LinkedHashSet<>();
        JsonElement variants = root.get("variants");
        if (variants instanceof JsonObject variantsObject) {
            for (Map.Entry<String, JsonElement> entry :
                    variantsObject.entrySet()) {
                JsonElement value = entry.getValue();
                collectVariantValue(value, references);
            }
        }
        JsonElement multipart = root.get("multipart");
        if (multipart != null
                && multipart.isJsonArray()) {
            for (JsonElement part :
                    multipart.getAsJsonArray()) {
                if (!(part instanceof JsonObject partObject)) {
                    continue;
                }
                collectApply(
                        partObject.get("apply"),
                        references);
            }
        }
        return List.copyOf(references);
    }

    private static void collectVariantValue(
            JsonElement value,
            Set<String> output) {
        if (value == null) {
            return;
        }
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(element ->
                    collectVariantValue(element, output));
            return;
        }
        String model = stringModel(value);
        if (model != null) {
            output.add(model);
        }
    }

    private static void collectApply(
            JsonElement apply,
            Set<String> output) {
        if (apply == null) {
            return;
        }
        if (apply.isJsonArray()) {
            apply.getAsJsonArray().forEach(element ->
                    collectApply(element, output));
            return;
        }
        String model = stringModel(apply);
        if (model != null) {
            output.add(model);
        }
    }

    private static String stringModel(
            JsonElement element) {
        if (element instanceof JsonObject object) {
            return string(object.get("model"));
        }
        return string(element);
    }

    private static String normalize(
            String raw,
            String defaultNamespace) {
        if (raw == null
                || raw.isBlank()
                || raw.startsWith("#")) {
            return null;
        }
        String trimmed = raw.trim();
        String withNamespace = trimmed.indexOf(':') >= 0
                ? trimmed
                : defaultNamespace + ':' + trimmed;
        Identifier parsed =
                Identifier.tryParse(withNamespace);
        return parsed == null
                ? null
                : parsed.toString();
    }

    private static String string(JsonElement value) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static Optional<byte[]> read(
            ResourceManager resources,
            Identifier blockstateId) {
        try {
            Resource resource = resources.getResource(
                    blockstateId)
                    .orElse(null);
            if (resource == null) {
                return Optional.empty();
            }
            try (var input = resource.open()) {
                return Optional.of(
                        input.readAllBytes());
            }
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> parse(
            byte[] encoded) {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(
                            encoded,
                            StandardCharsets.UTF_8));
            return parsed instanceof JsonObject object
                    ? Optional.of(object)
                    : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * 中文：回退选中的具体模型 ID 与其非空源纹理键。
     *
     * English: The concrete fallback model id and its nonempty source texture
     * keys.
     */
    public record Selected(
            String modelId,
            List<String> sourceTextureKeys) {
        public Selected {
            modelId = Objects.requireNonNull(
                    modelId,
                    "modelId");
            sourceTextureKeys = List.copyOf(
                    Objects.requireNonNull(
                            sourceTextureKeys,
                            "sourceTextureKeys"));
        }
    }
}
