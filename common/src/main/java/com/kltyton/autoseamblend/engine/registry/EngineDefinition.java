package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.List;
import java.util.Objects;

/**
 * 中文：一个可选引擎的中立描述与链接门；不携带任何 Loader 或第三方类型。
 * English: Loader-neutral description and linkage gate for one optional engine; it carries no
 * loader or third-party types.
 */
public record EngineDefinition(
        EngineDescriptor descriptor,
        List<String> acceptedVersions,
        List<String> hooks) {
    public EngineDefinition {
        Objects.requireNonNull(descriptor, "descriptor");
        acceptedVersions = List.copyOf(Objects.requireNonNull(
                acceptedVersions, "acceptedVersions"));
        hooks = List.copyOf(Objects.requireNonNull(hooks, "hooks"));
        if (acceptedVersions.isEmpty()
                || acceptedVersions.stream().anyMatch(
                        version -> version == null || version.isBlank())) {
            throw new IllegalArgumentException(
                    "acceptedVersions must contain non-blank versions");
        }
        if (!acceptedVersions.contains(descriptor.expectedVersion())) {
            throw new IllegalArgumentException(
                    "acceptedVersions must contain the descriptor expectedVersion");
        }
        if (hooks.stream().anyMatch(hook -> hook == null || hook.isBlank())) {
            throw new IllegalArgumentException("hooks must contain non-blank paths");
        }
    }

    /**
     * 中文：集中构造稳定描述，避免各 Loader 复制描述符拼装逻辑。
     * English: Centralizes stable descriptor construction so loaders do not duplicate it.
     */
    public static EngineDefinition of(
            String engineId,
            EngineFamily family,
            String formatId,
            String expectedVersion,
            String hookContract,
            String... hooks) {
        return ofVersions(
                engineId,
                family,
                formatId,
                List.of(expectedVersion),
                hookContract,
                hooks);
    }

    /**
     * 中文：为共享同一格式与适配器的多个已审计二进制实现构造定义；首项是规范版本。
     * English: Defines audited binary implementations that share one format and adapter; the
     * first version is canonical.
     */
    public static EngineDefinition ofVersions(
            String engineId,
            EngineFamily family,
            String formatId,
            List<String> acceptedVersions,
            String hookContract,
            String... hooks) {
        List<String> versions = List.copyOf(
                Objects.requireNonNull(acceptedVersions, "acceptedVersions"));
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("acceptedVersions must not be empty");
        }
        return new EngineDefinition(
                new EngineDescriptor(
                        engineId,
                        family,
                        formatId,
                        engineId,
                        versions.get(0),
                        hookContract),
                versions,
                List.of(hooks));
    }

    public boolean acceptsVersion(String version) {
        return version != null && acceptedVersions.contains(version);
    }
}
