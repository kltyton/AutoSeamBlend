package com.kltyton.autoseamblend.authoring.model;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;

/**
 * 中文：一个精确方块创作文档的规范项目 IR。
 *
 * English: Canonical project IR for one exact block authoring document.
 *
 * @param targetBlockId 中文：目标方块 ID。 / English: Target block id.
 * @param sourceTextureId 中文：源纹理 ID。 / English: Source texture id.
 * @param originalModelId 中文：原始模型 ID。 / English: Original model id.
 * @param requestedMethod 中文：请求的连接方法。 / English: Requested connection method.
 * @param resolvedMethod 中文：解析后的具体方法。 / English: Resolved concrete method.
 * @param compatibility 中文：是否按兼容策略补齐。 / English: Whether completion follows the compatibility policy.
 * @param pane 中文：是否应用玻璃板语义。 / English: Whether pane semantics apply.
 * @param sourceTextureKeys 中文：保留的源纹理键列表。 / English: Preserved source texture keys.
 */
public record ManagedAuthoringRule(
        String targetBlockId,
        String sourceTextureId,
        String originalModelId,
        ConnectionMethod requestedMethod,
        ConnectionMethod resolvedMethod,
        boolean compatibility,
        boolean pane,
        List<String> sourceTextureKeys) {
    public ManagedAuthoringRule {
        targetBlockId = identifier(
                targetBlockId,
                "targetBlockId");
        sourceTextureId = identifier(
                sourceTextureId,
                "sourceTextureId");
        originalModelId = identifier(
                originalModelId,
                "originalModelId");
        requestedMethod = Objects.requireNonNull(
                requestedMethod,
                "requestedMethod");
        resolvedMethod = Objects.requireNonNull(
                resolvedMethod,
                "resolvedMethod");
        if (resolvedMethod == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException(
                    "resolvedMethod must be concrete");
        }
        if (requestedMethod != ConnectionMethod.AUTO
                && requestedMethod != resolvedMethod) {
            throw new IllegalArgumentException(
                    "manual method must equal resolved method");
        }
        sourceTextureKeys = List.copyOf(
                Objects.requireNonNull(
                        sourceTextureKeys,
                        "sourceTextureKeys"));
        if (sourceTextureKeys.stream().anyMatch(
                key -> key == null
                        || !key.matches(
                                "[A-Za-z0-9_.-]+"))
                || sourceTextureKeys.stream()
                                .distinct()
                                .count()
                        != sourceTextureKeys.size()) {
            throw new IllegalArgumentException(
                    "source texture keys must be unique model variables");
        }
    }

    public String targetNamespace() {
        return namespace(targetBlockId);
    }

    public String targetPath() {
        return path(targetBlockId);
    }

    public String textureNamespace() {
        return namespace(sourceTextureId);
    }

    public String texturePath() {
        return path(sourceTextureId);
    }

    public String managedStem() {
        return targetNamespace() + '/' + targetPath();
    }

    private static String identifier(
            String raw,
            String label) {
        Objects.requireNonNull(raw, label);
        int separator = raw.indexOf(':');
        if (separator <= 0
                || separator != raw.lastIndexOf(':')
                || separator == raw.length() - 1) {
            throw new IllegalArgumentException(
                    label + " must be namespace:path");
        }
        String namespace = raw.substring(0, separator);
        String path = raw.substring(separator + 1);
        if (!namespace.matches("[a-z0-9_.-]+")
                || !path.matches("[a-z0-9/._-]+")
                || path.startsWith("/")
                || path.endsWith("/")
                || path.contains("//")) {
            throw new IllegalArgumentException(
                    label + " is not a canonical resource identifier: " + raw);
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                        label + " has an unsafe path segment: " + raw);
            }
        }
        return raw;
    }

    private static String namespace(String id) {
        return id.substring(0, id.indexOf(':'));
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }
}
