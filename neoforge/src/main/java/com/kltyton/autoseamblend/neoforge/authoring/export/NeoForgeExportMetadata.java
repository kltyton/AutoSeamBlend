package com.kltyton.autoseamblend.neoforge.authoring.export;

import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.foundation.Constants;
import net.neoforged.fml.ModList;

/**
 * 中文：NeoForge 只提供 ModList、版本和 Loader 元数据接线；注册与组装位于 Common。
 *
 * English: NeoForge supplies only ModList, version, and Loader metadata;
 * registration and assembly live in Common.
 */
public final class NeoForgeExportMetadata {
    private NeoForgeExportMetadata() {}

    public static NativeExportRuntime.RuntimeMetadata metadata(
            String engineId) {
        return new NativeExportRuntime.RuntimeMetadata(
                "1.21.1",
                "neoforge",
                installedVersion(engineId),
                Constants.VERSION);
    }

    private static String installedVersion(
            String engineId) {
        return ModList.get()
                .getModContainerById(engineId)
                .map(container ->
                        container.getModInfo()
                                .getVersion()
                                .toString())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "engine disappeared during export: "
                                        + engineId));
    }
}
