package com.kltyton.autoseamblend.compat.ctm_mod.reload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.reload.rule.ParsedRuleDocument;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * 中文：NeoForge 独占的 CTM Mod 规则文档识别与扩展解析。
 * English: Recognizes and parses NeoForge-only CTM Mod rule extensions.
 */
public final class CtmModRuleDocumentCodec {
    private CtmModRuleDocumentCodec() {}

    public static boolean recognizedManagedDocument(String documentPath) {
        String path = normalizePath(documentPath);
        return path.endsWith(".png.mcmeta") && path.contains("/textures/");
    }

    public static Optional<ParsedRuleDocument> parseManaged(
            String documentPath,
            byte[] source) throws IOException {
        String path = normalizePath(documentPath);
        if (!recognizedManagedDocument(path)) {
            return Optional.empty();
        }
        String resourceId = managedResourceId(path).orElseThrow(() ->
                new IOException("MANAGED_DOCUMENT_RESOURCE_ID_INVALID"));
        try {
            return parse(path, resourceId, json(source));
        } catch (RuntimeException exception) {
            throw new IOException("NATIVE_DOCUMENT_JSON_INVALID", exception);
        }
    }

    public static Optional<ParsedRuleDocument> parseNative(
            String resourceId,
            byte[] source) {
        Identifier id = resourceId == null ? null : Identifier.tryParse(resourceId);
        if (id == null
                || !id.getPath().startsWith("textures/")
                || !id.getPath().endsWith(".png.mcmeta")) {
            return Optional.empty();
        }
        return parse(id.getPath(), id.toString(), json(source));
    }

    private static Optional<ParsedRuleDocument> parse(
            String documentPath,
            String resourceId,
            JsonObject root) {
        JsonElement encoded = root.get("ctm");
        if (!(encoded instanceof JsonObject ctm) || !ctm.has("method")) {
            return Optional.empty();
        }
        Optional<ConnectionMethod> method = ConnectionMethod.parse(string(ctm.get("method")));
        Optional<Boolean> compatibility = compatibility(ctm.get("compatibility"));
        String target = blockId(string(ctm.get("target")));
        if (method.isEmpty() || compatibility.isEmpty() || target == null) {
            return Optional.empty();
        }
        String explicitId = string(ctm.get("id"));
        String identity = explicitId == null || explicitId.isEmpty()
                ? target
                : explicitId;
        return Optional.of(new ParsedRuleDocument(
                EngineFamily.CTM_MOD,
                identity,
                documentPath,
                resourceId,
                method.orElseThrow(),
                compatibility.orElseThrow(),
                List.of(target),
                Optional.of(root.toString())));
    }

    private static Optional<Boolean> compatibility(JsonElement value) {
        if (value == null) {
            return Optional.of(true);
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            return Optional.empty();
        }
        return Optional.of(value.getAsBoolean());
    }

    private static String blockId(String value) {
        Identifier id = value == null ? null : Identifier.tryParse(value);
        return id == null ? null : id.toString();
    }

    private static String string(JsonElement value) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static JsonObject json(byte[] source) {
        JsonElement parsed = JsonParser.parseString(
                new String(Objects.requireNonNull(source, "source"), StandardCharsets.UTF_8));
        if (parsed instanceof JsonObject root) {
            return root;
        }
        throw new IllegalArgumentException("NATIVE_DOCUMENT_JSON_INVALID");
    }

    private static Optional<String> managedResourceId(String documentPath) {
        if (!documentPath.startsWith("assets/")) {
            return Optional.empty();
        }
        int namespaceEnd = documentPath.indexOf('/', "assets/".length());
        if (namespaceEnd < 0 || namespaceEnd == documentPath.length() - 1) {
            return Optional.empty();
        }
        Identifier id = Identifier.tryParse(
                documentPath.substring("assets/".length(), namespaceEnd)
                        + ':'
                        + documentPath.substring(namespaceEnd + 1));
        return Optional.ofNullable(id).map(Identifier::toString);
    }

    private static String normalizePath(String documentPath) {
        return Objects.requireNonNull(documentPath, "documentPath").replace('\\', '/');
    }
}
