package com.kltyton.autoseamblend.forge.compat.continuity.bootstrap;

import com.kltyton.autoseamblend.compat.continuity.document.ContinuityPropertiesCaptureHooks;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityAcceptedHolderHooks;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityProcessorListHooks;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityNativeDocumentCatalog;
import com.kltyton.autoseamblend.forge.compat.continuity.runtime.ContinuityProcessorMetadata;
import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.compat.continuity.authoring.materialize.ContinuityConnectionTextureSourceProvider;
import com.kltyton.autoseamblend.compat.continuity.authoring.export.ContinuityNativeExportProvider;
import com.kltyton.autoseamblend.forge.compat.continuity.preview.ContinuityPreviewBootstrap;
import com.kltyton.autoseamblend.forge.compat.continuity.runtime.ContinuityNativeQueryOwnership;
import com.kltyton.autoseamblend.forge.compat.continuity.runtime.ForgeContinuityProcessorListHooks;
import com.kltyton.autoseamblend.compat.continuity.runtime.texture.ContinuityGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.runtime.publication.NativeGenerationParticipants;

/** 中文：只在引擎已安装时注册链接 NeoContinuity 的运行时服务。 / English: Registers NeoContinuity-linked runtime services only when the engine is installed. */
public final class ContinuityRuntimeBootstrap {
    private ContinuityRuntimeBootstrap() {}

    public static void register() {
        ContinuityProcessorListHooks.install(ForgeContinuityProcessorListHooks.INSTANCE);
        ContinuityPropertiesCaptureHooks.install(
                ContinuityNativeDocumentCatalog.INSTANCE);
        ContinuityAcceptedHolderHooks.install(ContinuityProcessorMetadata::register);
        ContinuityGeneratedStateSprites.register();
        ContinuityPreviewBootstrap.register();
        ConnectionTextureSources.register(
                ContinuityConnectionTextureSourceProvider
                        .INSTANCE);
        EngineQueryRouter.registerNativeQueryOwnership(
                ContinuityNativeQueryOwnership.INSTANCE);
        NativeGenerationParticipants.register(
                ContinuityNativeQueryOwnership.INSTANCE);
        NativeExportRuntime.register(
                ContinuityNativeExportProvider.INSTANCE);
    }
}
