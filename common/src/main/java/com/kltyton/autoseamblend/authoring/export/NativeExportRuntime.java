package com.kltyton.autoseamblend.authoring.export;

import com.kltyton.autoseamblend.engine.EngineIdentifiers;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中文：保存通用 provider 注册表并组装不可变导出 IR；Loader 只提供运行时元数据。
 *
 * English: Owns the neutral provider registry and immutable export-IR assembly;
 * the Loader supplies runtime metadata only.
 */
public final class NativeExportRuntime {
    private static final ConcurrentHashMap<String, NativeExportProvider> PROVIDERS =
            new ConcurrentHashMap<>();

    private NativeExportRuntime() {}

    public static void register(NativeExportProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String engineId = EngineIdentifiers.require(provider.engineId());
        NativeExportProvider previous = PROVIDERS.putIfAbsent(engineId, provider);
        if (previous != null
                && previous != provider
                && !previous.getClass().equals(provider.getClass())) {
            throw new IllegalStateException(
                    "native export provider already registered: " + engineId);
        }
    }

    public static ManagedExportIr assemble(
            String engineId,
            ExportDraft draft,
            RuntimeMetadata metadata)
            throws IOException {
        return assemble(
                engineId,
                List.of(Objects.requireNonNull(draft, "draft")),
                metadata);
    }

    public static ManagedExportIr assemble(
            String engineId,
            List<ExportDraft> drafts,
            RuntimeMetadata metadata)
            throws IOException {
        engineId = EngineIdentifiers.require(engineId);
        drafts = List.copyOf(Objects.requireNonNull(drafts, "drafts"));
        Objects.requireNonNull(metadata, "metadata");
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException(
                    "native export requires at least one draft");
        }
        NativeExportProvider provider = PROVIDERS.get(engineId);
        if (provider == null) {
            throw new IllegalStateException(
                    "native export provider unavailable: " + engineId);
        }
        long surfaceGeneration = drafts.get(0).surfaceGeneration();
        if (drafts.stream().anyMatch(draft ->
                draft.surfaceGeneration() != surfaceGeneration)) {
            throw new IllegalArgumentException(
                    "native export drafts span surface generations");
        }
        ArrayList<ManagedExportIr.Rule> rules = new ArrayList<>(drafts.size());
        for (int order = 0; order < drafts.size(); order++) {
            rules.add(provider.assemble(order, drafts.get(order)));
        }
        return new ManagedExportIr(
                surfaceGeneration,
                managedGenerationHash(drafts),
                metadata.minecraftVersion(),
                metadata.loader(),
                engineId,
                metadata.engineVersion(),
                metadata.autoSeamBlendVersion(),
                rules);
    }

    public static String managedGenerationHash(List<ExportDraft> drafts) {
        drafts = List.copyOf(Objects.requireNonNull(drafts, "drafts"));
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("hash requires at least one draft");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ExportDraft draft : drafts) {
                digest.update(draft.managedGenerationHash()
                        .getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static boolean available(String engineId) {
        return PROVIDERS.containsKey(
                EngineIdentifiers.require(engineId));
    }

    /**
     * 中文：导出 IR 的 Loader/version 元数据；不包含 Loader 类型。
     *
     * English: Loader/version metadata for export IR without Loader types.
     *
     * @param minecraftVersion 中文：目标 Minecraft 版本。 / English: Target Minecraft version.
     * @param loader 中文：Loader 标识。 / English: Loader identifier.
     * @param engineVersion 中文：当前引擎版本。 / English: Selected engine version.
     * @param autoSeamBlendVersion 中文：产品版本。 / English: Product version.
     */
    public record RuntimeMetadata(
            String minecraftVersion,
            String loader,
            String engineVersion,
            String autoSeamBlendVersion) {
        public RuntimeMetadata {
            minecraftVersion = text(minecraftVersion, "minecraftVersion");
            loader = text(loader, "loader");
            engineVersion = text(engineVersion, "engineVersion");
            autoSeamBlendVersion = text(
                    autoSeamBlendVersion,
                    "autoSeamBlendVersion");
        }

        private static String text(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}
