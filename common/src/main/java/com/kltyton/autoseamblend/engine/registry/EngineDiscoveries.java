package com.kltyton.autoseamblend.engine.registry;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** 中文：组合 Loader 版本查询与公共类资源探测。 / English: Composes Loader version lookup with shared class-resource probing. */
public final class EngineDiscoveries {
    private EngineDiscoveries() {
    }

    public static EngineDiscovery classpath(
            Function<String, Optional<String>> installedVersion,
            Class<?> classLoaderAnchor) {
        Objects.requireNonNull(installedVersion, "installedVersion");
        Objects.requireNonNull(classLoaderAnchor, "classLoaderAnchor");
        return new EngineDiscovery() {
            @Override
            public Optional<String> installedVersion(String modId) {
                return Objects.requireNonNull(
                        installedVersion.apply(modId), "installedVersion result");
            }

            @Override
            public boolean hookPresent(String resourcePath) {
                Objects.requireNonNull(resourcePath, "resourcePath");
                return classLoaderAnchor.getClassLoader().getResource(resourcePath) != null;
            }
        };
    }
}
