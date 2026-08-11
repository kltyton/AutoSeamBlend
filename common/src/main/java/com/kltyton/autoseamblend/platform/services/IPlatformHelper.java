package com.kltyton.autoseamblend.platform.services;

import java.nio.file.Path;

/** 中文：最小 Loader 边界；这里不能出现 Fabric、Forge 或 NeoForge 类型。 / English: Minimal Loader boundary; no Fabric/Forge/NeoForge type may appear here. */
public interface IPlatformHelper {
    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    default Path getGameDirectory() {
        return Path.of("").toAbsolutePath().normalize();
    }

    default Path getConfigDirectory() {
        return getGameDirectory().resolve("config");
    }
}
