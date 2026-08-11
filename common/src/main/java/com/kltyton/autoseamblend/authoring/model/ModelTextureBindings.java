package com.kltyton.autoseamblend.authoring.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 中文：解析模型父链和纹理变量绑定，不负责任何资源获取。
 *
 * English: Resolves model parent chains and texture-variable bindings without
 * owning resource acquisition.
 */
public final class ModelTextureBindings {
    private static final int MAX_PARENT_DEPTH = 64;

    private ModelTextureBindings() {}

    /**
     * 中文：读取模型文档并返回能解析到指定纹理的变量名，保留文档和变量的原始顺序。
     *
     * English: Reads model documents and returns variable names resolving to the
     * requested texture, preserving document and variable order.
     */
    public static List<String> resolve(
            String modelId,
            String sourceTextureId,
            ModelDocumentSource documents,
            IdentifierParser identifiers) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(sourceTextureId, "sourceTextureId");
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(identifiers, "identifiers");

        String current = identifiers.parse(modelId);
        String source = identifiers.parse(sourceTextureId);
        if (current == null || source == null) {
            throw new IllegalArgumentException(
                    "MODEL_OR_TEXTURE_ID_INVALID");
        }

        LinkedHashMap<String, String> definitions =
                new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        boolean terminated = false;
        for (int depth = 0;
                depth < MAX_PARENT_DEPTH;
                depth++) {
            if (!visited.add(current)) {
                throw new IllegalArgumentException(
                        "MODEL_PARENT_CYCLE");
            }
            Optional<byte[]> encoded = documents.read(current);
            if (encoded == null || encoded.isEmpty()) {
                terminated = true;
                break;
            }
            ModelDocument document = parse(encoded.orElseThrow());
            document.textureDefinitions().forEach(
                    definitions::putIfAbsent);

            String parent = document.parentId();
            if (parent == null) {
                terminated = true;
                break;
            }
            String next = identifiers.parse(parent);
            if (next == null) {
                throw new IllegalArgumentException(
                        "MODEL_PARENT_ID_INVALID");
            }
            current = next;
        }
        if (!terminated) {
            throw new IllegalArgumentException(
                    "MODEL_PARENT_DEPTH_EXCEEDED");
        }

        ArrayList<String> keys = new ArrayList<>();
        for (String key : definitions.keySet()) {
            String resolved = resolveAlias(key, definitions);
            if (resolved != null
                    && source.equals(identifiers.parse(resolved))) {
                keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    /**
     * 中文：把一个已读取的 JSON 模型文档转换为公共 DTO。
     *
     * English: Converts an already-read JSON model document into the common DTO.
     */
    public static ModelDocument parse(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        JsonElement parsed = JsonParser.parseString(
                new String(encoded, StandardCharsets.UTF_8));
        if (!(parsed instanceof JsonObject object)) {
            throw new IllegalArgumentException(
                    "MODEL_DOCUMENT_NOT_OBJECT");
        }

        LinkedHashMap<String, String> definitions =
                new LinkedHashMap<>();
        JsonElement textures = object.get("textures");
        if (textures instanceof JsonObject textureObject) {
            for (Map.Entry<String, JsonElement> entry
                    : textureObject.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()) {
                    definitions.put(
                            entry.getKey(),
                            value.getAsString());
                }
            }
        }
        JsonElement parent = object.get("parent");
        String parentId = parent != null
                && parent.isJsonPrimitive()
                && parent.getAsJsonPrimitive().isString()
                ? parent.getAsString()
                : null;
        return new ModelDocument(parentId, definitions);
    }

    private static String resolveAlias(
            String key,
            Map<String, String> definitions) {
        Set<String> aliases = new HashSet<>();
        String value = definitions.get(key);
        while (value != null && value.startsWith("#")) {
            String alias = value.substring(1);
            if (!aliases.add(alias)) {
                throw new IllegalArgumentException(
                        "MODEL_TEXTURE_ALIAS_CYCLE");
            }
            value = definitions.get(alias);
        }
        return value;
    }

    /**
     * 中文：由 Loader 提供的模型资源读取桥；空 Optional 表示模型缺失并终止父链。
     *
     * English: Loader-provided model resource bridge; an empty Optional means a
     * missing model and terminates the parent chain.
     */
    @FunctionalInterface
    public interface ModelDocumentSource {
        Optional<byte[]> read(String modelId);
    }

    /**
     * 中文：由 Loader 提供的资源标识解析桥，用于保留各版本 ResourceLocation 语义。
     *
     * English: Loader-provided resource-identifier bridge preserving each
     * version's ResourceLocation semantics.
     */
    @FunctionalInterface
    public interface IdentifierParser {
        String parse(String raw);
    }

    /**
     * 中文：模型文档的项目自有不可变表示。
     *
     * English: Project-owned immutable representation of one model document.
     *
     * @param parentId 中文：父模型 ID。 / English: Parent model id.
     * @param textureDefinitions 中文：模型文档的纹理定义映射。 / English: Texture definitions of the model document.
     */
    public record ModelDocument(
            String parentId,
            Map<String, String> textureDefinitions) {
        public ModelDocument {
            textureDefinitions = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(
                            textureDefinitions,
                            "textureDefinitions")));
        }
    }
}
