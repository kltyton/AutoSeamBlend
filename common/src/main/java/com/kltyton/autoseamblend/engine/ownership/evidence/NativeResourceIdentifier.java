package com.kltyton.autoseamblend.engine.ownership.evidence;

import java.util.Objects;
import java.util.Optional;

/**
 * 中文：在不引用 Loader 或 Minecraft 标识符类型的前提下规范化原生资源标识符。
 *
 * English: Normalizes native resource identifiers without referencing Loader or Minecraft
 * identifier types.
 */
public final class NativeResourceIdentifier {
    private NativeResourceIdentifier() {}

    public static Optional<String> textureId(
            String reference,
            String defaultNamespace) {
        if (reference == null
                || reference.isBlank()
                || reference.startsWith("#")) {
            return Optional.empty();
        }
        String normalized = reference.trim();
        if (normalized.startsWith("textures/")) {
            normalized = normalized.substring("textures/".length());
        }
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - ".png".length());
        }
        return canonical(normalized, defaultNamespace);
    }

    public static Optional<String> spriteId(String pngFile) {
        Optional<Parts> parts = parts(pngFile);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        String path = parts.orElseThrow().path();
        if (!path.startsWith("textures/")
                || !path.endsWith(".png")) {
            return Optional.empty();
        }
        return Optional.of(parts.orElseThrow().namespace()
                + ':'
                + path.substring(
                        "textures/".length(),
                        path.length() - ".png".length()));
    }

    public static Optional<String> textureFile(String spriteId) {
        return parts(spriteId).map(parts -> parts.namespace()
                + ":textures/"
                + parts.path()
                + ".png");
    }

    public static Optional<String> metadataFile(String spriteId) {
        return textureFile(spriteId).map(value -> value + ".mcmeta");
    }

    public static Optional<String> modelFile(String modelId) {
        return parts(modelId).map(parts -> parts.namespace()
                + ":models/"
                + parts.path()
                + ".json");
    }

    public static Optional<String> namespace(String identifier) {
        return parts(identifier).map(Parts::namespace);
    }

    private static Optional<String> canonical(
            String value,
            String defaultNamespace) {
        Objects.requireNonNull(defaultNamespace, "defaultNamespace");
        String candidate = value.indexOf(':') >= 0
                ? value
                : defaultNamespace + ':' + value;
        return parts(candidate).map(parts -> parts.namespace()
                + ':'
                + parts.path());
    }

    private static Optional<Parts> parts(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        int separator = identifier.indexOf(':');
        if (separator <= 0
                || separator != identifier.lastIndexOf(':')
                || separator == identifier.length() - 1) {
            return Optional.empty();
        }
        String namespace = identifier.substring(0, separator);
        String path = identifier.substring(separator + 1);
        if (!validNamespace(namespace) || !validPath(path)) {
            return Optional.empty();
        }
        return Optional.of(new Parts(namespace, path));
    }

    private static boolean validNamespace(String value) {
        return !value.isEmpty()
                && value.chars().allMatch(character ->
                        character >= 'a' && character <= 'z'
                                || character >= '0' && character <= '9'
                                || character == '_'
                                || character == '-'
                                || character == '.');
    }

    private static boolean validPath(String value) {
        return !value.isEmpty()
                && value.chars().allMatch(character ->
                        character >= 'a' && character <= 'z'
                                || character >= '0' && character <= '9'
                                || character == '_'
                                || character == '-'
                                || character == '.'
                                || character == '/');
    }

    private record Parts(String namespace, String path) {}
}
