package com.kltyton.autoseamblend.compat.fusion.evidence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceIdentifier;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceSource;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceSource.SheetFramePolicy;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceSource.TextureResourceState;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeSlotEvidence;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 中文：经中立资源端口遍历 Fusion 模型与元数据，并生成公共逐槽证据。
 * English: Traverses Fusion models and metadata through a neutral resource port and emits slots.
 */
public final class FusionSlotEvidenceResolver {
    private static final int MAX_MODEL_DEPTH = 16;

    private FusionSlotEvidenceResolver() {}

    public static List<NativeSlot> resolve(
            String documentId,
            JsonObject root,
            ConnectionMethod method,
            NativeResourceSource resources,
            FusionLayoutResolver layouts) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(layouts, "layouts");
        Optional<FusionCarrier> carrier = resolveCarrier(
                documentId,
                root,
                resources);
        if (carrier.isEmpty()) {
            return unknown(method);
        }
        FusionCarrier texture = carrier.orElseThrow();
        Optional<FusionSheetLayout> layout = layouts.resolve(
                method,
                texture.declaredLayout());
        if (layout.isEmpty()) {
            return unknown(method);
        }
        FusionSheetLayout resolved = layout.orElseThrow();
        TextureResourceState state = resources.inspectTexture(
                texture.spriteId(),
                resolved.columns(),
                resolved.rows(),
                SheetFramePolicy.FUSION);
        if (state == TextureResourceState.MISSING) {
            return NativeSlotEvidence.declaredMissing(
                    method,
                    NativeSlotEvidence.FULL_CTM_SLOTS,
                    texture.spriteId());
        }
        if (state == TextureResourceState.INVALID) {
            return unknown(method);
        }
        List<Integer> domain = NativeSlotEvidence.domain(
                method,
                NativeSlotEvidence.FULL_CTM_SLOTS);
        if (domain.size() != resolved.logicalSlotCount()) {
            return NativeSlotEvidence.unknown(domain);
        }
        return NativeSlotEvidence.present(
                domain,
                texture.spriteId());
    }

    /**
     * 中文：只解析 Fusion 文档引用到的唯一 connecting 载体，不执行 Loader 资源捕获。
     * English: Resolves the sole connecting carrier referenced by a Fusion document without
     * performing Loader-side resource capture.
     */
    public static Optional<FusionCarrier> resolveCarrier(
            String documentId,
            JsonObject root,
            NativeResourceSource resources) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(resources, "resources");
        String defaultNamespace = NativeResourceIdentifier
                .namespace(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "invalid document id"));
        LinkedHashSet<String> modelIds = new LinkedHashSet<>();
        collectModelIds(root.get("default_model_overrides"), defaultNamespace, modelIds);
        collectModelIds(root.get("append_models"), defaultNamespace, modelIds);
        if (modelIds.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashSet<String> textureIds = new LinkedHashSet<>();
        LinkedHashSet<String> visitedModels = new LinkedHashSet<>();
        for (String modelId : modelIds) {
            collectModelTextures(modelId, resources, textureIds, visitedModels, 0);
        }
        List<FusionCarrier> connecting = textureIds.stream()
                .map(sprite -> NativeResourceIdentifier
                        .metadataFile(sprite)
                        .flatMap(resources::read)
                        .flatMap(bytes -> connectingMetadata(sprite, bytes)))
                .flatMap(Optional::stream)
                .toList();
        return connecting.size() == 1
                ? Optional.of(connecting.getFirst())
                : Optional.empty();
    }

    private static List<NativeSlot> unknown(
            ConnectionMethod method) {
        return NativeSlotEvidence.unknown(
                method,
                NativeSlotEvidence.FULL_CTM_SLOTS);
    }

    private static void collectModelIds(
            JsonElement entry,
            String defaultNamespace,
            Set<String> output) {
        if (entry == null) {
            return;
        }
        if (entry.isJsonArray()) {
            entry.getAsJsonArray().forEach(value ->
                    collectModelIds(
                            value,
                            defaultNamespace,
                            output));
            return;
        }
        if (entry.isJsonObject()) {
            collectModelIds(
                    entry.getAsJsonObject().get("model"),
                    defaultNamespace,
                    output);
            return;
        }
        if (entry.isJsonPrimitive()
                && entry.getAsJsonPrimitive().isString()) {
            NativeResourceIdentifier.textureId(
                            entry.getAsString(),
                            defaultNamespace)
                    .ifPresent(output::add);
        }
    }

    private static void collectModelTextures(
            String modelId,
            NativeResourceSource resources,
            Set<String> output,
            Set<String> visited,
            int depth) {
        if (depth >= MAX_MODEL_DEPTH || !visited.add(modelId)) {
            return;
        }
        Optional<byte[]> resource = NativeResourceIdentifier
                .modelFile(modelId)
                .flatMap(resources::read);
        if (resource.isEmpty()) {
            return;
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(
                            resource.orElseThrow(),
                            StandardCharsets.UTF_8));
            if (!(parsed instanceof JsonObject object)) {
                return;
            }
            root = object;
        } catch (RuntimeException exception) {
            return;
        }
        String defaultNamespace = NativeResourceIdentifier
                .namespace(modelId)
                .orElseThrow();
        if (root.get("textures") instanceof JsonObject textures) {
            textures.entrySet().stream()
                    .map(java.util.Map.Entry::getValue)
                    .filter(JsonElement::isJsonPrimitive)
                    .map(JsonElement::getAsString)
                    .filter(value -> !value.startsWith("#"))
                    .map(value -> NativeResourceIdentifier.textureId(
                            value,
                            defaultNamespace))
                    .flatMap(Optional::stream)
                    .forEach(output::add);
        }
        JsonElement parent = root.get("parent");
        if (parent != null
                && parent.isJsonPrimitive()
                && parent.getAsJsonPrimitive().isString()) {
            NativeResourceIdentifier.textureId(
                            parent.getAsString(),
                            defaultNamespace)
                    .ifPresent(value -> collectModelTextures(
                            value,
                            resources,
                            output,
                            visited,
                            depth + 1));
        }
    }

    private static Optional<FusionCarrier> connectingMetadata(
            String spriteId,
            byte[] document) {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(document, StandardCharsets.UTF_8));
            if (!(parsed instanceof JsonObject root)
                    || !(root.get("fusion") instanceof JsonObject fusion)) {
                return Optional.empty();
            }
            JsonElement type = fusion.get("type");
            if (type == null
                    || !type.isJsonPrimitive()
                    || !type.getAsJsonPrimitive().isString()
                    || !"connecting".equals(type.getAsString())) {
                return Optional.empty();
            }
            JsonElement layout = fusion.get("layout");
            if (layout != null
                    && (!layout.isJsonPrimitive()
                            || !layout.getAsJsonPrimitive().isString())) {
                return Optional.empty();
            }
            return Optional.of(new FusionCarrier(
                    spriteId,
                    layout == null
                            ? Optional.empty()
                            : Optional.of(layout.getAsString())));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public record FusionSheetLayout(
            int columns,
            int rows,
            int logicalSlotCount) {
        public FusionSheetLayout {
            if (columns <= 0 || rows <= 0 || logicalSlotCount < 0) {
                throw new IllegalArgumentException(
                        "invalid Fusion sheet layout");
            }
        }
    }

    @FunctionalInterface
    public interface FusionLayoutResolver {
        Optional<FusionSheetLayout> resolve(
                ConnectionMethod method,
                Optional<String> declaredLayout);
    }

    public record FusionCarrier(
            String spriteId,
            Optional<String> declaredLayout) {
        public FusionCarrier {
            Objects.requireNonNull(spriteId, "spriteId");
            declaredLayout = Objects.requireNonNull(
                    declaredLayout,
                    "declaredLayout");
        }
    }
}
