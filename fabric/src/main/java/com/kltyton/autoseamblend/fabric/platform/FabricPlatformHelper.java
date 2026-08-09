package com.kltyton.autoseamblend.fabric.platform;

import com.kltyton.autoseamblend.platform.services.IPlatformHelper;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 中文：1.21.1 Fabric 平台服务实现；不含任何引擎或 UI 类型。
 * English: 1.21.1 Fabric platform service implementation without engine or UI types.
 */
public final class FabricPlatformHelper implements IPlatformHelper {
    private final FabricLoader loader = FabricLoader.getInstance();

    @Override
    public String getPlatformName() {
        return "Fabric 1.21.1";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return loader.isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return loader.isDevelopmentEnvironment();
    }

    @Override
    public Path getGameDirectory() {
        return loader.getGameDir().toAbsolutePath().normalize();
    }

    @Override
    public Path getConfigDirectory() {
        return loader.getConfigDir().toAbsolutePath().normalize();
    }
}
