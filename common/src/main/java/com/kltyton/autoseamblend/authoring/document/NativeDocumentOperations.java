package com.kltyton.autoseamblend.authoring.document;

import com.kltyton.autoseamblend.authoring.property.NativePropertyPatchApplier;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：原生文档合并与属性补丁的可注入格式边界。
 * English: Injectable format boundary for native-document merging and property patching.
 */
public interface NativeDocumentOperations {
    byte[] mergeSource(
            EngineFamily family,
            String path,
            byte[] existing,
            byte[] desired) throws IOException;

    byte[] resolveProperty(
            EngineFamily family,
            String path,
            byte[] source,
            Map<String, Optional<String>> values) throws IOException;

    /**
     * 中文：注册 Loader 独占格式家族（如 NeoForge 的 CTM Mod）的文档合并与属性补丁实现。
     *
     * English: Registers document merging and property patching for a
     * Loader-exclusive format family such as CTM Mod on NeoForge.
     */
    static void registerFamily(
            EngineFamily family,
            FamilyOperations operations) {
        NativeDocumentOperationExtensions.register(
                family,
                operations);
    }

    static NativeDocumentOperations shared() {
        return Shared.INSTANCE;
    }

    /**
     * 中文：Loader 独占格式家族的文档合并与属性补丁契约。
     *
     * English: Contract for Loader-exclusive format-family document merging
     * and property patching.
     */
    interface FamilyOperations {
        byte[] mergeSource(
                EngineFamily family,
                String path,
                byte[] existing,
                byte[] desired) throws IOException;

        byte[] resolveProperty(
                EngineFamily family,
                String path,
                byte[] source,
                Map<String, Optional<String>> values) throws IOException;
    }

    enum Shared implements NativeDocumentOperations {
        INSTANCE;

        @Override
        public byte[] mergeSource(
                EngineFamily family,
                String path,
                byte[] existing,
                byte[] desired) throws IOException {
            FamilyOperations operations =
                    NativeDocumentOperationExtensions.get(
                            Objects.requireNonNull(
                                    family,
                                    "family"));
            if (operations != null) {
                return operations.mergeSource(
                        family,
                        path,
                        existing,
                        desired);
            }
            return NativeDocumentMerge.mergeSource(family, path, existing, desired);
        }

        @Override
        public byte[] resolveProperty(
                EngineFamily family,
                String path,
                byte[] source,
                Map<String, Optional<String>> values) throws IOException {
            FamilyOperations operations =
                    NativeDocumentOperationExtensions.get(
                            Objects.requireNonNull(
                                    family,
                                    "family"));
            if (operations != null) {
                return operations.resolveProperty(
                        family,
                        path,
                        source,
                        values);
            }
            return NativePropertyPatchApplier.resolve(family, path, source, values);
        }
    }
}
