package com.kltyton.autoseamblend.reload.rule;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorField;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorResolver;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：MCPatcher、Fusion 与 Athena 创作扩展的 Loader 中立解析边界。
 * English: Loader-neutral parser boundary for MCPatcher, Fusion, and Athena authoring extensions.
 */
public final class RuleDocumentCodec {
    private static final CopyOnWriteArrayList<Extension> EXTENSIONS =
            new CopyOnWriteArrayList<>();

    private RuleDocumentCodec() {}

    /**
     * 中文：注册 Loader 独占格式家族（如 NeoForge 的 CTM Mod）的规则文档解析扩展。
     *
     * English: Registers a rule-document parsing extension for a Loader-exclusive
     * format family such as CTM Mod on NeoForge.
     */
    public static void register(Extension extension) {
        EXTENSIONS.add(Objects.requireNonNull(extension, "extension"));
    }

    public static boolean recognizedManagedDocument(
            String documentPath) {
        String path = normalizePath(documentPath);
        if ((path.endsWith(".properties")
                        && path.contains("/optifine/ctm/"))
                || (path.endsWith(".json")
                        && path.contains("/fusion/model_modifiers/blocks/"))
                || (path.endsWith(".json")
                        && (path.contains("/athena/")
                                || path.contains("/blockstates/")))) {
            return true;
        }
        for (Extension extension : EXTENSIONS) {
            if (extension.recognizedManagedDocument(path)) {
                return true;
            }
        }
        return false;
    }

    public static boolean recognizedNativeDocument(
            String resourceId) {
        ResourceLocation id = resourceId == null
                ? null
                : ResourceLocation.tryParse(resourceId);
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return path.endsWith(".json")
                || path.endsWith(".png.mcmeta");
    }

    public static Optional<ParsedRuleDocument> parseManaged(
            String documentPath,
            byte[] source,
            NativeBlockSelectorResolver selectorResolver)
            throws IOException {
        String path = normalizePath(documentPath);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(selectorResolver, "selectorResolver");
        for (Extension extension : EXTENSIONS) {
            Optional<ParsedRuleDocument> parsed =
                    extension.parseManaged(
                            path,
                            source,
                            selectorResolver);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        String resourceId = managedResourceId(path)
                .orElseThrow(() -> new IOException(
                        "MANAGED_DOCUMENT_RESOURCE_ID_INVALID"));
        if (path.endsWith(".properties")
                && path.contains("/optifine/ctm/")) {
            return parseMCPatcher(
                    path,
                    resourceId,
                    source,
                    selectorResolver);
        }
        JsonObject root;
        try {
            root = json(source);
        } catch (RuntimeException exception) {
            throw new IOException(
                    "NATIVE_DOCUMENT_JSON_INVALID",
                    exception);
        }
        return parseJson(
                path,
                resourceId,
                root);
    }

    public static Optional<ParsedRuleDocument> parseNative(
            String resourceId,
            byte[] source) {
        Objects.requireNonNull(source, "source");
        for (Extension extension : EXTENSIONS) {
            Optional<ParsedRuleDocument> parsed =
                    extension.parseNative(
                            resourceId,
                            source);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        ResourceLocation id = resourceId == null
                ? null
                : ResourceLocation.tryParse(resourceId);
        if (id == null) {
            return Optional.empty();
        }
        JsonElement parsed = JsonParser.parseString(
                new String(source, StandardCharsets.UTF_8));
        if (!(parsed instanceof JsonObject root)) {
            return Optional.empty();
        }
        return parseJson(
                id.getPath(),
                id.toString(),
                root);
    }

    /**
     * 中文：Loader 独占格式家族的规则文档识别与解析契约。
     *
     * English: Rule-document recognition and parsing contract for a
     * Loader-exclusive format family.
     */
    public interface Extension {
        boolean recognizedManagedDocument(String documentPath);

        Optional<ParsedRuleDocument> parseManaged(
                String documentPath,
                byte[] source,
                NativeBlockSelectorResolver selectorResolver)
                throws IOException;

        Optional<ParsedRuleDocument> parseNative(
                String resourceId,
                byte[] source);
    }

    private static Optional<ParsedRuleDocument> parseMCPatcher(
            String documentPath,
            String resourceId,
            byte[] source,
            NativeBlockSelectorResolver selectorResolver)
            throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(
                new String(source, StandardCharsets.UTF_8)));
        Optional<ConnectionMethod> method = method(
                properties.getProperty("method"));
        Optional<Boolean> compatibility = compatibility(
                properties.getProperty("compatibility"));
        if (method.isEmpty() || compatibility.isEmpty()) {
            return Optional.empty();
        }
        NativeBlockSelectorField matching =
                NativeBlockSelectorField.parse(
                        properties.containsKey("matchBlocks"),
                        properties.getProperty("matchBlocks"),
                        selectorResolver);
        NativeBlockSelectorField connecting =
                NativeBlockSelectorField.parse(
                        properties.containsKey("connectBlocks"),
                        properties.getProperty("connectBlocks"),
                        selectorResolver);
        String explicitTarget = blockId(properties.getProperty("id"));
        List<String> targets = explicitTarget == null
                ? matching.blockIds()
                : List.of(explicitTarget);
        return Optional.of(new ParsedRuleDocument(
                EngineFamily.MCPATCHER,
                entryIdentity(
                        properties.getProperty("id"),
                        connecting.firstDisplayValue().orElse(null),
                        matching.firstDisplayValue().orElse(null),
                        documentPath),
                documentPath,
                resourceId,
                method.orElseThrow(),
                compatibility.orElseThrow(),
                targets,
                Optional.empty()));
    }

    private static Optional<ParsedRuleDocument> parseJson(
            String documentPath,
            String resourceId,
            JsonObject root) {
        ResourceLocation id = ResourceLocation.tryParse(resourceId);
        if (id == null) {
            return Optional.empty();
        }
        String path = id.getPath();
        boolean managedPath = documentPath.startsWith("assets/");
        if ((managedPath
                        ? documentPath.contains("/fusion/model_modifiers/blocks/")
                        : path.startsWith("fusion/model_modifiers/blocks/"))
                && path.endsWith(".json")) {
            return parseFusion(
                    documentPath,
                    id.toString(),
                    root);
        }
        if ((path.startsWith("athena/")
                        || path.startsWith("blockstates/"))
                && path.endsWith(".json")) {
            return parseAthena(
                    documentPath,
                    id,
                    root);
        }
        return Optional.empty();
    }

    private static Optional<ParsedRuleDocument> parseFusion(
            String documentPath,
            String resourceId,
            JsonObject root) {
        if (!root.has("method")) {
            return Optional.empty();
        }
        Optional<ConnectionMethod> method = method(string(root.get("method")));
        Optional<Boolean> compatibility = compatibility(root.get("compatibility"));
        JsonElement encodedTargets = root.get("targets");
        if (method.isEmpty()
                || compatibility.isEmpty()
                || encodedTargets == null
                || !encodedTargets.isJsonArray()) {
            return Optional.empty();
        }
        ArrayList<String> targets = new ArrayList<>();
        for (JsonElement encodedTarget : encodedTargets.getAsJsonArray()) {
            String target = blockId(string(encodedTarget));
            if (target != null) {
                targets.add(target);
            }
        }
        return Optional.of(jsonDocument(
                EngineFamily.FUSION,
                entryIdentity(
                        string(root.get("id")),
                        null,
                        targets.isEmpty() ? null : targets.getFirst(),
                        documentPath),
                documentPath,
                resourceId,
                method.orElseThrow(),
                compatibility.orElseThrow(),
                targets,
                root));
    }

    private static Optional<ParsedRuleDocument> parseAthena(
            String documentPath,
            ResourceLocation resourceId,
            JsonObject root) {
        if (!root.has("method")
                || !root.has("athena:loader")) {
            return Optional.empty();
        }
        Optional<ConnectionMethod> method = method(string(root.get("method")));
        Optional<Boolean> compatibility = compatibility(root.get("compatibility"));
        String target = athenaTarget(resourceId);
        if (method.isEmpty()
                || compatibility.isEmpty()
                || target == null) {
            return Optional.empty();
        }
        return Optional.of(jsonDocument(
                EngineFamily.ATHENA,
                entryIdentity(
                        string(root.get("id")),
                        null,
                        target,
                        documentPath),
                documentPath,
                resourceId.toString(),
                method.orElseThrow(),
                compatibility.orElseThrow(),
                List.of(target),
                root));
    }

    private static ParsedRuleDocument jsonDocument(
            EngineFamily family,
            String entryId,
            String documentPath,
            String resourceId,
            ConnectionMethod requestedMethod,
            boolean compatibility,
            List<String> targets,
            JsonObject root) {
        return new ParsedRuleDocument(
                family,
                entryId,
                documentPath,
                resourceId,
                requestedMethod,
                compatibility,
                targets,
                Optional.of(root.toString()));
    }

    private static JsonObject json(byte[] source) {
        JsonElement parsed = JsonParser.parseString(
                new String(source, StandardCharsets.UTF_8));
        if (parsed instanceof JsonObject root) {
            return root;
        }
        throw new IllegalArgumentException(
                "NATIVE_DOCUMENT_JSON_INVALID");
    }

    private static Optional<ConnectionMethod> method(String value) {
        return value == null
                ? Optional.empty()
                : ConnectionMethod.parse(value);
    }

    private static Optional<Boolean> compatibility(String value) {
        if (value == null
                || value.isBlank()
                || "true".equals(value)) {
            return Optional.of(true);
        }
        if ("false".equals(value)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    private static Optional<Boolean> compatibility(JsonElement value) {
        if (value == null) {
            return Optional.of(true);
        }
        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            return Optional.empty();
        }
        return Optional.of(value.getAsBoolean());
    }

    private static String string(JsonElement value) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static String entryIdentity(
            String explicitId,
            String connectionFallback,
            String matchingFallback,
            String documentPath) {
        for (String candidate : List.of(
                Objects.toString(explicitId, ""),
                Objects.toString(connectionFallback, ""),
                Objects.toString(matchingFallback, ""),
                Objects.requireNonNull(documentPath, "documentPath"))) {
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
                "managed document identity is empty");
    }

    private static String athenaTarget(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        String prefix;
        if (path.startsWith("athena/")) {
            prefix = "athena/";
        } else if (path.startsWith("blockstates/")) {
            prefix = "blockstates/";
        } else {
            return null;
        }
        return blockId(
                resourceId.getNamespace()
                        + ':'
                        + path.substring(
                                prefix.length(),
                                path.length() - ".json".length()));
    }

    private static String blockId(String value) {
        ResourceLocation id = value == null
                ? null
                : ResourceLocation.tryParse(value);
        return id == null ? null : id.toString();
    }

    private static Optional<String> managedResourceId(String documentPath) {
        if (!documentPath.startsWith("assets/")) {
            return Optional.empty();
        }
        int namespaceEnd = documentPath.indexOf('/', "assets/".length());
        if (namespaceEnd < 0
                || namespaceEnd == documentPath.length() - 1) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(
                documentPath.substring("assets/".length(), namespaceEnd)
                        + ':'
                        + documentPath.substring(namespaceEnd + 1));
        return Optional.ofNullable(id).map(ResourceLocation::toString);
    }

    private static String normalizePath(String documentPath) {
        return Objects.requireNonNull(documentPath, "documentPath")
                .replace('\\', '/');
    }
}
