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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Resolves a concrete model referenced by a blockstate when the conventional model is absent. */
public final class BlockstatePreferredModelResolution {
    private BlockstatePreferredModelResolution() {}

    public static Optional<Result> resolve(
            ResourceManager resources,
            String targetBlockId,
            String sourceTextureId) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(targetBlockId, "targetBlockId");
        Objects.requireNonNull(sourceTextureId, "sourceTextureId");
        ResourceLocation target = ResourceLocation.tryParse(targetBlockId);
        if (target == null) {
            return Optional.empty();
        }
        ResourceLocation blockstateId = ResourceLocation.fromNamespaceAndPath(
                target.getNamespace(),
                "blockstates/" + target.getPath() + ".json");
        Optional<JsonObject> root = read(resources, blockstateId).flatMap(
                BlockstatePreferredModelResolution::parse);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        for (String reference : modelReferences(root.orElseThrow())) {
            String modelId = normalize(reference, target.getNamespace());
            if (modelId == null) {
                continue;
            }
            try {
                List<String> keys = MinecraftModelTextureBindings.resolve(
                        resources, modelId, sourceTextureId);
                if (!keys.isEmpty()) {
                    return Optional.of(new Result(modelId, keys));
                }
            } catch (RuntimeException ignored) {
                // A malformed optional candidate must not block later valid blockstate models.
            }
        }
        return Optional.empty();
    }

    private static List<String> modelReferences(JsonObject root) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        JsonElement variants = root.get("variants");
        if (variants instanceof JsonObject variantsObject) {
            for (Map.Entry<String, JsonElement> entry : variantsObject.entrySet()) {
                collect(entry.getValue(), references);
            }
        }
        JsonElement multipart = root.get("multipart");
        if (multipart != null && multipart.isJsonArray()) {
            for (JsonElement part : multipart.getAsJsonArray()) {
                if (part instanceof JsonObject partObject) {
                    collect(partObject.get("apply"), references);
                }
            }
        }
        return List.copyOf(references);
    }

    private static void collect(JsonElement value, Set<String> output) {
        if (value == null) {
            return;
        }
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(element -> collect(element, output));
            return;
        }
        String model = value instanceof JsonObject object
                ? string(object.get("model"))
                : string(value);
        if (model != null) {
            output.add(model);
        }
    }

    private static String string(JsonElement value) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static String normalize(String raw, String defaultNamespace) {
        if (raw == null || raw.isBlank() || raw.startsWith("#")) {
            return null;
        }
        String trimmed = raw.trim();
        String namespaced = trimmed.indexOf(':') >= 0
                ? trimmed
                : defaultNamespace + ':' + trimmed;
        ResourceLocation parsed = ResourceLocation.tryParse(namespaced);
        return parsed == null ? null : parsed.toString();
    }

    private static Optional<byte[]> read(
            ResourceManager resources,
            ResourceLocation location) {
        try {
            Resource resource = resources.getResource(location).orElse(null);
            if (resource == null) {
                return Optional.empty();
            }
            try (var input = resource.open()) {
                return Optional.of(input.readAllBytes());
            }
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> parse(byte[] encoded) {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(encoded, StandardCharsets.UTF_8));
            return parsed instanceof JsonObject object
                    ? Optional.of(object)
                    : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public record Result(
            String selectedModelId,
            List<String> sourceTextureKeys) {
        public Result {
            Objects.requireNonNull(selectedModelId, "selectedModelId");
            sourceTextureKeys = List.copyOf(
                    Objects.requireNonNull(sourceTextureKeys, "sourceTextureKeys"));
        }
    }
}
