package com.kltyton.autoseamblend.fabric.compat.continuity.bootstrap;

import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.compat.continuity.authoring.export.ContinuityNativeExportProvider;
import com.kltyton.autoseamblend.compat.continuity.authoring.materialize.ContinuityConnectionTextureSourceProvider;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityAcceptedHolderHooks;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityNativeDocumentCatalog;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityPropertiesCaptureHooks;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityProcessorListHooks;
import com.kltyton.autoseamblend.compat.continuity.runtime.texture.ContinuityGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.fabric.compat.continuity.preview.FabricContinuityPreviewBootstrap;
import com.kltyton.autoseamblend.fabric.compat.continuity.runtime.FabricContinuityModelLifecycle;
import com.kltyton.autoseamblend.fabric.compat.continuity.runtime.FabricContinuityNativeQueryOwnership;
import com.kltyton.autoseamblend.fabric.compat.continuity.runtime.FabricContinuityProcessorListHooks;
import com.kltyton.autoseamblend.fabric.compat.continuity.runtime.FabricContinuityProcessorMetadata;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricEngineBootstrap;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricModelCapture;
import com.kltyton.autoseamblend.runtime.publication.NativeGenerationParticipants;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：只注册不直接链接 Continuity 类型的兼容边界。
 * English: Registers the compat boundary without directly linking Continuity
 * types.
 */
public enum ContinuityBootstrap
        implements FabricEngineBootstrap {
    INSTANCE;

    private static final ResourceLocation CAPTURE_FINISH_PHASE =
            ResourceLocation.fromNamespaceAndPath(
                    "autoseamblend",
                    "continuity_capture_finish");

    @Override
    public void register() {
        ContinuityProcessorListHooks.install(
                FabricContinuityProcessorListHooks.INSTANCE);
        ContinuityPropertiesCaptureHooks.install(
                ContinuityNativeDocumentCatalog.INSTANCE);
        ContinuityAcceptedHolderHooks.install(
                FabricContinuityProcessorMetadata::register);
        ContinuityGeneratedStateSprites.register();
        FabricContinuityPreviewBootstrap.register();
        ConnectionTextureSources.register(
                ContinuityConnectionTextureSourceProvider
                        .INSTANCE);
        EngineQueryRouter.registerNativeQueryOwnership(
                FabricContinuityNativeQueryOwnership.INSTANCE);
        NativeGenerationParticipants.register(
                FabricContinuityNativeQueryOwnership.INSTANCE);
        NativeExportRuntime.register(
                ContinuityNativeExportProvider.INSTANCE);
        PreparableModelLoadingPlugin.<Long>register(
                (state, executor) ->
                        CompletableFuture.completedFuture(
                                FabricModelCapture.begin()),
                (session, context) -> {
                    FabricContinuityModelLifecycle.begin(
                            session);
                    var afterBake =
                            context.modifyModelAfterBake();
                    afterBake.register(
                            ModelModifier.OVERRIDE_PHASE,
                            (model, modifierContext) ->
                                    FabricContinuityModelLifecycle
                                            .captureBase(
                                                    session,
                                                    model,
                                                    modifierContext));
                    afterBake.addPhaseOrdering(
                            ModelModifier.WRAP_LAST_PHASE,
                            CAPTURE_FINISH_PHASE);
                    afterBake.register(
                            CAPTURE_FINISH_PHASE,
                            (model, modifierContext) ->
                                    FabricContinuityModelLifecycle
                                            .finishCapture(
                                                    session,
                                                    model,
                                                    modifierContext));
                });
    }
}
