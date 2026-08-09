package com.kltyton.autoseamblend.fabric.authoring.export;

import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.foundation.Constants;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 中文：Fabric 导出 IR 的 Loader/版本元数据。
 * English: Loader/version metadata for Fabric export IR.
 */
public final class FabricExportMetadata {
    private FabricExportMetadata() {}

    public static NativeExportRuntime.RuntimeMetadata metadata(
            String engineId) {
        return new NativeExportRuntime.RuntimeMetadata(
                "1.21.1",
                "fabric",
                installedVersion(engineId),
                Constants.VERSION);
    }

    private static String installedVersion(
            String engineId) {
        return FabricLoader.getInstance()
                .getModContainer(engineId)
                .map(container -> container.getMetadata()
                        .getVersion()
                        .getFriendlyString())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "engine disappeared during export: "
                                        + engineId));
    }
}
