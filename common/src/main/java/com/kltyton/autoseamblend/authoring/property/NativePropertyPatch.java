package com.kltyton.autoseamblend.authoring.property;

import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：一次显式可视化属性编辑产生的最小、引擎类型无关原生文档补丁。
 *
 * English: Minimal engine-type-free native-document patch produced by one
 * explicit visual property edit.
 */
public record NativePropertyPatch(
        EngineFamily family,
        String documentPath,
        String templateDocumentPath,
        byte[] sourceDocument,
        Map<String, Optional<String>> values) {
    public NativePropertyPatch {
        family = Objects.requireNonNull(family, "family");
        requireNormalizedPath(documentPath, "documentPath");
        requireNormalizedPath(templateDocumentPath, "templateDocumentPath");
        sourceDocument = Objects.requireNonNull(sourceDocument, "sourceDocument").clone();
        LinkedHashMap<String, Optional<String>> copy = new LinkedHashMap<>();
        Objects.requireNonNull(values, "values").forEach((key, value) ->
                copy.put(
                        Objects.requireNonNull(key, "property key"),
                        Objects.requireNonNull(value, "property value")));
        values = Collections.unmodifiableMap(copy);
    }

    @Override
    public byte[] sourceDocument() {
        return sourceDocument.clone();
    }

    private static void requireNormalizedPath(String value, String label) {
        if (value == null || value.isBlank() || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(
                    label + " must be a normalized resource-pack path");
        }
    }
}
