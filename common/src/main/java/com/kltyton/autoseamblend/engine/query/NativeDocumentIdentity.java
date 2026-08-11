package com.kltyton.autoseamblend.engine.query;

import java.util.Objects;
import java.util.Optional;

/**
 * 中文：一个已接受原生文档的引擎中立资源身份；资源包 ID 只在引擎实际暴露时存在。
 *
 * English:
 * Engine-neutral resource identity of one accepted native document. The pack ID is present only
 * when the engine actually exposes it.
 */
public record NativeDocumentIdentity(
        Optional<String> packId,
        String resourceId) {
    public NativeDocumentIdentity {
        packId = Objects.requireNonNull(packId, "packId");
        packId.ifPresent(value -> requireText(value, "packId"));
        requireText(resourceId, "resourceId");
    }

    public static NativeDocumentIdentity resourceOnly(String resourceId) {
        return new NativeDocumentIdentity(Optional.empty(), resourceId);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
