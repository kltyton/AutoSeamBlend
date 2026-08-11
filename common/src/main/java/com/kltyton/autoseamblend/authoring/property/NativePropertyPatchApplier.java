package com.kltyton.autoseamblend.authoring.property;

import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.authoring.document.json.LosslessJsonPatch;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 中文：执行引擎类型无关的原生文档属性补丁；只处理冻结字节和项目数据。
 *
 * English: Applies engine-neutral native-document property patches using only
 * frozen bytes and project data.
 */
public final class NativePropertyPatchApplier {
    private static final Set<String> MCPATCHER_KEYS = Set.of(
            "id", "matchBlocks", "connectBlocks", "faces", "connect", "layer",
            "tintBlock", "tiles", "method", "compatibility");
    private static final Set<String> ATHENA_KEYS = Set.of("id", "connect_to", "method", "compatibility");
    private static final Set<String> FUSION_KEYS = Set.of("id", "method", "compatibility");
    private static final Set<String> FUSION_METADATA_KEYS = Set.of("method", "compatibility");
    private NativePropertyPatchApplier() {}

    /**
     * 中文：在原生文档合并后按工作区顺序应用补丁，并拒绝捕获或字段冲突。
     *
     * English: Applies patches in workspace order after native-document merging,
     * rejecting capture and field conflicts.
     */
    public static void apply(
            Map<String, byte[]> transaction,
            List<NativePropertyPatch> patches)
            throws IOException {
        apply(transaction, patches, NativeDocumentOperations.shared());
    }

    public static void apply(
            Map<String, byte[]> transaction,
            List<NativePropertyPatch> patches,
            NativeDocumentOperations operations)
            throws IOException {
        Objects.requireNonNull(transaction, "transaction");
        NativeDocumentOperations resolvedOperations = Objects.requireNonNull(
                operations,
                "operations");
        LinkedHashMap<String, CapturedDocument> capturedByPath = new LinkedHashMap<>();
        LinkedHashMap<String, LinkedHashMap<String, Optional<String>>> valuesByPath = new LinkedHashMap<>();
        for (NativePropertyPatch patch : List.copyOf(Objects.requireNonNull(patches, "patches"))) {
            LinkedHashMap<String, Optional<String>> accumulated = valuesByPath.computeIfAbsent(
                    patch.documentPath(), ignored -> new LinkedHashMap<>());
            for (Map.Entry<String, Optional<String>> entry : patch.values().entrySet()) {
                Optional<String> previous = accumulated.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) {
                    throw new IOException("NATIVE_PROPERTY_PATCH_CONFLICT:"
                            + patch.documentPath() + ':' + entry.getKey());
                }
            }
            CapturedDocument first = capturedByPath.get(patch.documentPath());
            byte[] source;
            if (first == null) {
                first = new CapturedDocument(
                        patch.family(),
                        patch.templateDocumentPath(),
                        patch.sourceDocument());
                capturedByPath.put(patch.documentPath(), first);
                byte[] desired = transaction.get(patch.templateDocumentPath());
                source = first.source();
                if (desired != null) {
                    source = resolvedOperations.mergeSource(
                            patch.family(), patch.documentPath(), source, desired);
                }
                if (!patch.documentPath().equals(patch.templateDocumentPath())) {
                    transaction.remove(patch.templateDocumentPath());
                }
            } else {
                first.requireSameCapture(patch);
                source = transaction.get(patch.documentPath());
                if (source == null) {
                    throw new IOException("NATIVE_PROPERTY_DOCUMENT_MISSING:" + patch.documentPath());
                }
            }
            transaction.put(patch.documentPath(), resolvedOperations.resolveProperty(
                    patch.family(), patch.documentPath(), source, patch.values()));
        }
    }

    /**
     * 中文：将字段补丁应用到已捕获的原生主文档；不执行文件 I/O。
     *
     * English: Applies property values to a captured native principal document
     * without performing file I/O.
     */
    public static byte[] resolve(
            EngineFamily family,
            String documentPath,
            byte[] source,
            Map<String, Optional<String>> values)
            throws IOException {
        EngineFamily resolvedFamily = Objects.requireNonNull(family, "family");
        String resolvedPath = Objects.requireNonNull(documentPath, "documentPath");
        byte[] resolvedSource = Objects.requireNonNull(source, "source").clone();
        Map<String, Optional<String>> requestedValues = Objects.requireNonNull(
                values, "values");
        ArrayList<String> stableKeys = new ArrayList<>(requestedValues.keySet());
        stableKeys.forEach(key -> Objects.requireNonNull(key, "property key"));
        stableKeys.sort(String::compareTo);
        LinkedHashMap<String, Optional<String>> valueCopy = new LinkedHashMap<>();
        // 中文：新增字段按稳定键序写入；既有字段仍在原文本位置局部替换。
        // English: Insert new fields in stable key order while replacing existing fields in place.
        for (String key : stableKeys) {
            valueCopy.put(
                    Objects.requireNonNull(key, "property key"),
                    Objects.requireNonNull(
                            requestedValues.get(key), "property value"));
        }
        Map<String, Optional<String>> resolvedValues = Collections.unmodifiableMap(valueCopy);
        if (resolvedValues.isEmpty()) {
            return resolvedSource;
        }
        return switch (resolvedFamily) {
            case MCPATCHER -> {
                if (!resolvedPath.endsWith(".properties")
                        || !MCPATCHER_KEYS.containsAll(resolvedValues.keySet())) {
                    throw new IOException("NATIVE_PROPERTY_PATCH_UNSUPPORTED");
                }
                yield properties(resolvedSource, resolvedValues);
            }
            case ATHENA -> {
                if (!resolvedPath.endsWith(".json") || !ATHENA_KEYS.containsAll(resolvedValues.keySet())) {
                    throw new IOException("NATIVE_PROPERTY_PATCH_UNSUPPORTED");
                }
                yield rootJson(resolvedSource, resolvedValues, "ATHENA_DOCUMENT_JSON_INVALID");
            }
            default -> throw new IOException(
                    "LOADER_EXCLUSIVE_PROPERTY_PATCH_REQUIRES_ADAPTER");
            case FUSION -> {
                boolean metadata = resolvedPath.endsWith(".png.mcmeta");
                if ((!metadata && !resolvedPath.endsWith(".json"))
                        || !(metadata ? FUSION_METADATA_KEYS : FUSION_KEYS)
                                .containsAll(resolvedValues.keySet())) {
                    throw new IOException("NATIVE_PROPERTY_PATCH_UNSUPPORTED");
                }
                yield metadata
                        ? fusionMetadataJson(resolvedSource, resolvedValues)
                        : rootJson(resolvedSource, resolvedValues, "FUSION_DOCUMENT_JSON_INVALID");
            }
        };
    }

    private static byte[] rootJson(
            byte[] sourceBytes,
            Map<String, Optional<String>> values,
            String invalidCode)
            throws IOException {
        String resolved = LosslessJsonPatch.patchRootValues(
                decodeJson(sourceBytes, invalidCode),
                invalidCode,
                values,
                "NATIVE_PROPERTY_JSON_INVALID");
        return resolved.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] fusionMetadataJson(
            byte[] sourceBytes,
            Map<String, Optional<String>> values)
            throws IOException {
        String invalidCode = "FUSION_DOCUMENT_JSON_INVALID";
        String resolved = LosslessJsonPatch.patchNestedValues(
                decodeJson(sourceBytes, invalidCode),
                invalidCode,
                "fusion",
                true,
                values,
                "NATIVE_PROPERTY_JSON_INVALID");
        return resolved.getBytes(StandardCharsets.UTF_8);
    }

    private static String decodeJson(byte[] sourceBytes, String invalidCode) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(sourceBytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException(invalidCode, exception);
        }
    }

    private static byte[] properties(
            byte[] sourceBytes,
            Map<String, Optional<String>> values)
            throws IOException {
        String source;
        try {
            source = StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(sourceBytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("MCPATCHER_DOCUMENT_UTF8_INVALID", exception);
        }
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        boolean finalNewline = source.endsWith("\n") || source.endsWith("\r");
        ArrayList<String> output = new ArrayList<>();
        LinkedHashSet<String> emitted = new LinkedHashSet<>();
        for (String line : source.split("\\R", -1)) {
            String key = propertyKey(line);
            if (key == null || !values.containsKey(key)) {
                output.add(line);
                continue;
            }
            if (!emitted.add(key)) {
                continue;
            }
            values.get(key).ifPresent(value -> output.add(key + '=' + value));
        }
        values.forEach((key, value) -> {
            if (emitted.add(key)) {
                value.ifPresent(next -> output.add(key + '=' + next));
            }
        });
        if (finalNewline && !output.isEmpty() && output.get(output.size() - 1).isEmpty()) {
            output.remove(output.size() - 1);
        }
        String resolved = String.join(newline, output);
        if (finalNewline) {
            resolved += newline;
        }
        return resolved.getBytes(StandardCharsets.UTF_8);
    }

    private static String propertyKey(String line) {
        int start = 0;
        while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
            start++;
        }
        if (start == line.length() || line.charAt(start) == '#' || line.charAt(start) == '!') {
            return null;
        }
        boolean escaped = false;
        for (int index = start; index < line.length(); index++) {
            char value = line.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (value == '\\') {
                escaped = true;
            } else if (value == '=' || value == ':' || Character.isWhitespace(value)) {
                return line.substring(start, index);
            }
        }
        return line.substring(start);
    }

    private record CapturedDocument(
            EngineFamily family,
            String templateDocumentPath,
            byte[] source) {
        private CapturedDocument {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(templateDocumentPath, "templateDocumentPath");
            source = Objects.requireNonNull(source, "source").clone();
        }

        @Override
        public byte[] source() {
            return source.clone();
        }

        private void requireSameCapture(NativePropertyPatch patch) throws IOException {
            if (family != patch.family()
                    || !templateDocumentPath.equals(patch.templateDocumentPath())
                    || !Arrays.equals(source, patch.sourceDocument())) {
                throw new IOException("NATIVE_PROPERTY_DOCUMENT_CAPTURE_CONFLICT:"
                        + patch.documentPath());
            }
        }
    }
}
