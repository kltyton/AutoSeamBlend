package com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModCarrierLayout.CarrierSpec;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** 中文：只读解析 CTM Lib 方块状态、模型与纹理槽。 / English: Read-only CTM Lib blockstate/model texture-slot resolver. */
public final class CtmModNativeDocument {
    public static final String MODEL_TYPE =
            "ctm:connected_texture_model";
    private static final int MAX_MODEL_DEPTH = 16;

    private CtmModNativeDocument() {}

    public static List<NativeModel> read(
            ResourceLocation documentId,
            byte[] document,
            ResourceManager resources) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(resources, "resources");
        JsonObject root = parse(document).orElse(null);
        if (root == null) {
            return List.of();
        }
        ArrayList<JsonObject> encodedModels =
                new ArrayList<>();
        collectModels(
                root.get("variants"),
                encodedModels);
        JsonElement multipart = root.get("multipart");
        if (multipart != null && multipart.isJsonArray()) {
            multipart.getAsJsonArray().forEach(part -> {
                if (part instanceof JsonObject object) {
                    collectModels(
                            object.get("apply"),
                            encodedModels);
                }
            });
        }
        ArrayList<NativeModel> models =
                new ArrayList<>();
        for (JsonObject encoded : encodedModels) {
            String modelLocation = string(
                    encoded.get("model_location"));
            JsonObject variant = encoded.get("variant")
                            instanceof JsonObject object
                    ? object
                    : null;
            String kind = variant == null
                    ? null
                    : string(variant.get("kind"));
            ResourceLocation modelId = identifier(
                    modelLocation,
                    documentId.getNamespace());
            if (kind == null || modelId == null) {
                continue;
            }
            Map<String, String> textureSlots =
                    modelSlots(
                            modelId,
                            resources,
                            new LinkedHashSet<>(),
                            0);
            ArrayList<ResolvedCarrier> carriers =
                    new ArrayList<>();
            for (CarrierSpec spec : CtmModCarrierLayout
                    .forKind(kind)
                    .carriers()) {
                carriers.add(resolve(
                        spec,
                        textureSlots,
                        modelId.getNamespace()));
            }
            models.add(new NativeModel(
                    kind,
                    modelId,
                    carriers));
        }
        return List.copyOf(models);
    }

    public static List<NativeModel> read(
            ResourceLocation documentId,
            ResourceManager resources) {
        Optional<Resource> resource = resources.getResource(
                documentId);
        if (resource.isEmpty()) {
            return List.of();
        }
        try (var input = resource.orElseThrow().open()) {
            return read(
                    documentId,
                    input.readAllBytes(),
                    resources);
        } catch (IOException exception) {
            return List.of();
        }
    }

    public static Optional<ResourceLocation> resourceId(
            String packPath) {
        String[] segments = Objects.requireNonNull(
                        packPath,
                        "packPath")
                .split("/", 3);
        if (segments.length != 3
                || !"assets".equals(segments[0])) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                ResourceLocation.tryParse(
                        segments[1] + ':' + segments[2]));
    }

    public static String texturePath(ResourceLocation textureId) {
        Objects.requireNonNull(textureId, "textureId");
        return "assets/"
                + textureId.getNamespace()
                + "/textures/"
                + textureId.getPath()
                + ".png";
    }

    private static void collectModels(
            JsonElement encoded,
            List<JsonObject> output) {
        if (encoded == null) {
            return;
        }
        if (encoded.isJsonArray()) {
            encoded.getAsJsonArray().forEach(value ->
                    collectModels(value, output));
            return;
        }
        if (!(encoded instanceof JsonObject object)) {
            return;
        }
        String type = string(object.get("type"));
        if (MODEL_TYPE.equals(type)) {
            output.add(object);
            return;
        }
        object.entrySet().forEach(entry ->
                collectModels(entry.getValue(), output));
    }

    private static ResolvedCarrier resolve(
            CarrierSpec spec,
            Map<String, String> slots,
            String defaultNamespace) {
        for (String key : spec.textureKeys()) {
            if (!slots.containsKey(key)) {
                continue;
            }
            String value = resolveAlias(
                    slots.get(key),
                    slots,
                    new LinkedHashSet<>());
            return new ResolvedCarrier(
                    spec,
                    true,
                    Optional.ofNullable(identifier(
                            value,
                            defaultNamespace)));
        }
        return new ResolvedCarrier(
                spec,
                false,
                Optional.empty());
    }

    private static String resolveAlias(
            String value,
            Map<String, String> slots,
            Set<String> visited) {
        String current = value;
        while (current != null
                && current.startsWith("#")) {
            String key = current.substring(1);
            if (!visited.add(key)) {
                return null;
            }
            current = slots.get(key);
        }
        return current;
    }

    private static Map<String, String> modelSlots(
            ResourceLocation modelId,
            ResourceManager resources,
            Set<ResourceLocation> visited,
            int depth) {
        if (depth >= MAX_MODEL_DEPTH
                || !visited.add(modelId)) {
            return Map.of();
        }
        ResourceLocation file = new ResourceLocation(
                modelId.getNamespace(),
                "models/" + modelId.getPath() + ".json");
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) {
            return Map.of();
        }
        JsonObject root;
        try (InputStreamReader reader = new InputStreamReader(
                resource.orElseThrow().open(),
                StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!(parsed instanceof JsonObject object)) {
                return Map.of();
            }
            root = object;
        } catch (IOException | RuntimeException exception) {
            return Map.of();
        }
        LinkedHashMap<String, String> slots =
                new LinkedHashMap<>();
        String parent = string(root.get("parent"));
        ResourceLocation parentId = identifier(
                parent,
                modelId.getNamespace());
        if (parentId != null) {
            slots.putAll(modelSlots(
                    parentId,
                    resources,
                    visited,
                    depth + 1));
        }
        if (root.get("textures")
                instanceof JsonObject textures) {
            textures.entrySet().forEach(entry -> {
                String value = string(entry.getValue());
                if (value != null) {
                    slots.put(entry.getKey(), value);
                }
            });
        }
        return Map.copyOf(slots);
    }

    private static Optional<JsonObject> parse(byte[] bytes) {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(bytes, StandardCharsets.UTF_8));
            return parsed instanceof JsonObject object
                    ? Optional.of(object)
                    : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static ResourceLocation identifier(
            String value,
            String defaultNamespace) {
        if (value == null || value.isBlank()
                || value.startsWith("#")) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("textures/")) {
            normalized = normalized.substring(
                    "textures/".length());
        }
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - ".png".length());
        }
        return ResourceLocation.tryParse(
                normalized.indexOf(':') >= 0
                        ? normalized
                        : defaultNamespace + ':' + normalized);
    }

    private static String string(JsonElement value) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    public record NativeModel(
            String kind,
            ResourceLocation modelLocation,
            List<ResolvedCarrier> carriers) {
        public NativeModel {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(modelLocation, "modelLocation");
            carriers = List.copyOf(
                    Objects.requireNonNull(
                            carriers,
                            "carriers"));
        }
    }

    public record ResolvedCarrier(
            CarrierSpec spec,
            boolean declared,
            Optional<ResourceLocation> textureId) {
        public ResolvedCarrier {
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(textureId, "textureId");
        }
    }
}
