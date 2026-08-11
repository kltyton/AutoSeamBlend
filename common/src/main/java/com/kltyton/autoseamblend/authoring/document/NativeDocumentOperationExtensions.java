package com.kltyton.autoseamblend.authoring.document;

import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 中文：Loader 独占格式家族的文档合并与属性补丁注册表。
 *
 * English: Registry for Loader-exclusive format-family document merging and
 * property patching.
 */
final class NativeDocumentOperationExtensions {
    private static final ConcurrentMap<
                    EngineFamily,
                    NativeDocumentOperations.FamilyOperations>
            EXTENSIONS = new ConcurrentHashMap<>();

    private NativeDocumentOperationExtensions() {}

    static void register(
            EngineFamily family,
            NativeDocumentOperations.FamilyOperations operations) {
        EXTENSIONS.put(
                Objects.requireNonNull(family, "family"),
                Objects.requireNonNull(operations, "operations"));
    }

    static NativeDocumentOperations.FamilyOperations get(
            EngineFamily family) {
        return EXTENSIONS.get(family);
    }
}
