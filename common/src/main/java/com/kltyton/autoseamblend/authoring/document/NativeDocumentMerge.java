package com.kltyton.autoseamblend.authoring.document;

import com.kltyton.autoseamblend.authoring.document.json.LosslessJsonPatch;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 中文：显式保存时合并原生文档的无 I/O 规则，保留注释、未知字段和既有字段顺序。
 *
 * English: I/O-free native-document merge rules for explicit saves. They retain
 * comments, unknown fields, and existing field order.
 */
public final class NativeDocumentMerge {
    private static final Set<String> MCPATCHER_TEMPLATE_KEYS = Set.of("id", "matchBlocks", "matchTiles", "faces", "connect", "connectBlocks", "method", "tiles", "layer", "tintBlock", "compatibility");
    private static final Set<String> MCPATCHER_EXTENSION_KEYS = Set.of("id", "method", "compatibility");
    private static final String MCPATCHER_GENERATED_TILE_PREFIX = "autoseamblend:generated/";
    private static final String MCPATCHER_GENERATED_TILE_MARKER = "# AutoSeamBlend generated slots / AutoSeamBlend 生成槽位";
    private NativeDocumentMerge() {}

    public static byte[] mergeSource(EngineFamily family, String path, byte[] existing, byte[] desired) throws IOException {
        if (family == EngineFamily.MCPATCHER && path.endsWith(".properties")) return mergeProperties(existing, desired);
        if (path.endsWith(".json") || path.endsWith(".mcmeta")) return mergeJson(family, path, existing, desired);
        return desired.clone();
    }
    private static byte[] mergeProperties(byte[] existingBytes, byte[] desiredBytes) throws IOException {
        String existing = decodeUtf8(existingBytes, "MCPATCHER_DOCUMENT_UTF8_INVALID");
        String desired = decodeUtf8(desiredBytes, "MCPATCHER_TEMPLATE_UTF8_INVALID");
        LinkedHashMap<String, String> desiredLines = properties(desired);
        if (!MCPATCHER_TEMPLATE_KEYS.containsAll(desiredLines.keySet())) throw new IOException("MCPATCHER_TEMPLATE_KEY_INVALID");
        String newline = existing.contains("\r\n") ? "\r\n" : "\n";
        boolean finalNewline = existing.endsWith("\n") || existing.endsWith("\r");
        ArrayList<String> output = new ArrayList<>();
        LinkedHashSet<String> emitted = new LinkedHashSet<>();
        boolean generatedTiles = false;
        for (String line : existing.split("\\R", -1)) {
            if (line.strip().equals(MCPATCHER_GENERATED_TILE_MARKER)) { generatedTiles = true; output.add(line); continue; }
            if (line.isEmpty() && output.isEmpty() && existing.isEmpty()) continue;
            String key = propertyKey(line);
            if (key == null || !desiredLines.containsKey(key)) { output.add(line); continue; }
            if (MCPATCHER_EXTENSION_KEYS.contains(key) || isGeneratedTileLine(key, line, generatedTiles)) {
                if (emitted.add(key)) output.add(desiredLines.get(key));
                generatedTiles = false;
                continue;
            }
            output.add(line);
            emitted.add(key);
            if ("tiles".equals(key)) generatedTiles = false;
        }
        desiredLines.forEach((key, line) -> { if (MCPATCHER_EXTENSION_KEYS.contains(key) && emitted.add(key)) output.add(line); });
        if (finalNewline && !output.isEmpty() && output.get(output.size() - 1).isEmpty()) output.remove(output.size() - 1);
        String merged = String.join(newline, output);
        if (finalNewline) merged += newline;
        return merged.getBytes(StandardCharsets.UTF_8);
    }
    private static boolean isGeneratedTileLine(String key, String line, boolean markedGenerated) {
        if (!"tiles".equals(key)) return false;
        if (markedGenerated) return true;
        int separator = propertyValueStart(line);
        return separator >= 0 && line.substring(separator).stripLeading().startsWith(MCPATCHER_GENERATED_TILE_PREFIX);
    }
    private static int propertyValueStart(String line) { boolean escaped = false; for (int index = 0; index < line.length(); index++) { char value = line.charAt(index); if (escaped) escaped = false; else if (value == '\\') escaped = true; else if (value == '=' || value == ':') return index + 1; } return -1; }
    private static LinkedHashMap<String, String> properties(String source) throws IOException {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String line : source.split("\\R")) { String key = propertyKey(line); if (key != null && (!MCPATCHER_TEMPLATE_KEYS.contains(key) || values.putIfAbsent(key, line) != null)) throw new IOException("MCPATCHER_TEMPLATE_KEY_INVALID"); }
        return values;
    }
    private static String propertyKey(String line) {
        int start = 0; while (start < line.length() && Character.isWhitespace(line.charAt(start))) start++;
        if (start == line.length() || line.charAt(start) == '#' || line.charAt(start) == '!') return null;
        boolean escaped = false;
        for (int index = start; index < line.length(); index++) { char value = line.charAt(index); if (escaped) { escaped = false; continue; } if (value == '\\') { escaped = true; continue; } if (value == '=' || value == ':' || Character.isWhitespace(value)) return line.substring(start, index); }
        return line.substring(start);
    }
    private static byte[] mergeJson(EngineFamily family, String path, byte[] existingBytes, byte[] desiredBytes) throws IOException {
        String existing = decodeUtf8(existingBytes, "NATIVE_DOCUMENT_JSON_INVALID");
        String desired = decodeUtf8(desiredBytes, "NATIVE_TEMPLATE_JSON_INVALID");
        String merged = switch (family) {
            default -> throw new IOException(
                    "LOADER_EXCLUSIVE_MERGE_REQUIRES_ADAPTER");
            case FUSION -> mergeFusion(path, existing, desired);
            case ATHENA -> LosslessJsonPatch.replaceRootKeys(
                    existing,
                    "NATIVE_DOCUMENT_JSON_INVALID",
                    desired,
                    "NATIVE_TEMPLATE_JSON_INVALID",
                    "id",
                    "method",
                    "compatibility");
            case MCPATCHER -> LosslessJsonPatch.fillMissing(
                    existing,
                    "NATIVE_DOCUMENT_JSON_INVALID",
                    desired,
                    "NATIVE_TEMPLATE_JSON_INVALID");
        };
        return merged.getBytes(StandardCharsets.UTF_8);
    }
    private static String mergeFusion(String path, String existing, String desired) throws IOException {
        if (path.endsWith(".png.mcmeta")) {
            return LosslessJsonPatch.replaceNestedKeys(
                    existing,
                    "NATIVE_DOCUMENT_JSON_INVALID",
                    desired,
                    "NATIVE_TEMPLATE_JSON_INVALID",
                    "fusion",
                    false,
                    "method",
                    "compatibility");
        }
        if (path.contains("/fusion/model_modifiers/blocks/")) {
            return LosslessJsonPatch.replaceRootKeys(
                    existing,
                    "NATIVE_DOCUMENT_JSON_INVALID",
                    desired,
                    "NATIVE_TEMPLATE_JSON_INVALID",
                    "id",
                    "method",
                    "compatibility");
        }
        return LosslessJsonPatch.replaceRootKeys(
                existing,
                "NATIVE_DOCUMENT_JSON_INVALID",
                desired,
                "NATIVE_TEMPLATE_JSON_INVALID",
                "method",
                "compatibility");
    }
    private static String decodeUtf8(byte[] bytes, String error) throws IOException { try { return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); } catch (CharacterCodingException exception) { throw new IOException(error, exception); } }
}
