package com.kltyton.autoseamblend.neoforge.platform;

import com.kltyton.autoseamblend.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }

    @Override
    public Path getGameDirectory() {
        return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
    }

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
    }
}
