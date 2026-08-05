package com.kltyton.autoseamblend.fabric.compat.continuity.preview;

import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.authoring.preview.PreviewRuntime;
import com.kltyton.autoseamblend.compat.continuity.authoring.export.ContinuityNativeExportProvider;

/**
 * 中文：仅在引擎发现选中 Continuity 后加载其链接预览。
 * English: Loads the Continuity-linked preview only after engine discovery
 * selected Continuity.
 */
public final class FabricContinuityPreviewBootstrap {
    private FabricContinuityPreviewBootstrap() {}

    public static void register() {
        PreviewRuntime.register(
                FabricContinuityPreviewProvider.INSTANCE);
        NativeExportRuntime.register(
                ContinuityNativeExportProvider.INSTANCE);
    }
}
