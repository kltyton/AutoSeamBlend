package com.kltyton.autoseamblend.authoring.model;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** 中文：解析产生一个精确烘焙面精灵的模型纹理变量。 / English: Resolves model texture variables that produce one exact baked face sprite. */
public final class MinecraftModelTextureBindings {
    private MinecraftModelTextureBindings() {}

    public static List<String> resolve(
            ResourceManager resources,
            String modelId,
            String sourceTextureId) {
        Objects.requireNonNull(resources, "resources");
        String model = parseIdentifier(
                Objects.requireNonNull(modelId, "modelId"));
        String source = parseIdentifier(
                Objects.requireNonNull(sourceTextureId,
                        "sourceTextureId"));
        if (model == null || source == null) {
            throw new IllegalArgumentException(
                    "MODEL_OR_TEXTURE_ID_INVALID");
        }

        return ModelTextureBindings.resolve(
                model,
                source,
                modelIdValue -> read(resources, modelIdValue),
                MinecraftModelTextureBindings::parseIdentifier);
    }

    private static Optional<byte[]> read(
            ResourceManager resources,
            String modelId) {
        Identifier model = Identifier.tryParse(modelId);
        if (model == null) {
            return Optional.empty();
        }
        Identifier resourceId =
                Identifier.fromNamespaceAndPath(
                        model.getNamespace(),
                        "models/"
                                + model.getPath()
                                + ".json");
        Resource resource = resources
                .getResource(resourceId)
                .orElse(null);
        if (resource == null) {
            return Optional.empty();
        }
        try (var input = resource.open()) {
            return Optional.of(input.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "MODEL_DOCUMENT_READ_FAILED",
                    exception);
        }
    }

    private static String parseIdentifier(String raw) {
        Identifier parsed = Identifier.tryParse(raw);
        return parsed == null ? null : parsed.toString();
    }
}
