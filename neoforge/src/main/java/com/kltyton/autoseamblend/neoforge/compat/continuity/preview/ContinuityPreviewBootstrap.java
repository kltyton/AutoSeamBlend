package com.kltyton.autoseamblend.neoforge.compat.continuity.preview;

import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.authoring.preview.PreviewRuntime;
import com.kltyton.autoseamblend.authoring.preview.PreviewSceneQuadProcessorRegistry;
import com.kltyton.autoseamblend.compat.continuity.authoring.export.ContinuityNativeExportProvider;

/** 中文：仅在引擎发现选中 NeoContinuity 后加载其链接预览。 / English: Loads the NeoContinuity-linked preview only after engine discovery selected it. */
public final class ContinuityPreviewBootstrap {
    private ContinuityPreviewBootstrap() {}

    public static void register() {
        PreviewRuntime.register(ContinuityPreviewProvider.INSTANCE);
        PreviewSceneQuadProcessorRegistry.register(
                ContinuityPreviewSceneQuadProcessor.INSTANCE);
        NativeExportRuntime.register(
                ContinuityNativeExportProvider.INSTANCE);
    }
}
