package com.kltyton.autoseamblend.engine.query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：一个引擎对精确表面的查询观察；精确文档与未知原生效果可以同时存在，二者均不得被丢弃。
 *
 * English:
 * One engine's observation of an exact surface. Exact documents and unknown native effects may
 * coexist, and neither may be discarded.
 */
public record NativeQueryObservation(
        List<AcceptedNativeDocument> acceptedDocuments,
        Optional<String> unknownDiagnostic) {
    public NativeQueryObservation {
        acceptedDocuments = List.copyOf(Objects.requireNonNull(acceptedDocuments, "acceptedDocuments"));
        unknownDiagnostic = Objects.requireNonNull(unknownDiagnostic, "unknownDiagnostic");
        unknownDiagnostic.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("unknownDiagnostic must not be blank");
            }
        });
    }

    public static NativeQueryObservation noMatch() {
        return new NativeQueryObservation(List.of(), Optional.empty());
    }

    public static NativeQueryObservation exact(List<AcceptedNativeDocument> documents) {
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("an exact observation requires at least one document");
        }
        return new NativeQueryObservation(documents, Optional.empty());
    }

    public static NativeQueryObservation unknown(String diagnostic) {
        return new NativeQueryObservation(List.of(), Optional.of(diagnostic));
    }
}
