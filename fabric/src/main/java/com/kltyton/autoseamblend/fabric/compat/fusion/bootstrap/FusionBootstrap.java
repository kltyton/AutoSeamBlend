package com.kltyton.autoseamblend.fabric.compat.fusion.bootstrap;

import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.authoring.preview.PreviewRuntime;
import com.kltyton.autoseamblend.compat.fusion.authoring.export.FusionNativeExportProvider;
import com.kltyton.autoseamblend.compat.fusion.authoring.materialize.FusionConnectionTextureSourceProvider;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionModifierLifecycleHooks;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionMutableQuadHooks;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionPreviewProvider;
import com.kltyton.autoseamblend.compat.fusion.runtime.texture.FusionGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.fabric.compat.fusion.runtime.FabricFusionModelLifecycle;
import com.kltyton.autoseamblend.fabric.compat.fusion.runtime.FabricFusionModifierLifecycleHooks;
import com.kltyton.autoseamblend.fabric.compat.fusion.runtime.FabricFusionMutableQuadHooks;
import com.kltyton.autoseamblend.fabric.compat.fusion.runtime.FabricFusionNativeModelOwnershipProvider;
import com.kltyton.autoseamblend.fabric.compat.fusion.runtime.FabricFusionNativeQueryOwnership;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricEngineBootstrap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import java.util.concurrent.CompletableFuture;

/**
 * 中文：仅在不依赖第三方类型的发现流程选中 Fusion 后加载其链接运行时。
 *
 * English: Loads the Fusion-linked runtime only after third-party-free
 * discovery selected Fusion.
 */
public enum FusionBootstrap
        implements FabricEngineBootstrap {
    INSTANCE;

    @Override
    public void register() {
        FusionModifierLifecycleHooks.install(
                FabricFusionModifierLifecycleHooks.INSTANCE);
        FusionMutableQuadHooks.install(
                FabricFusionMutableQuadHooks.INSTANCE);
        FusionGeneratedStateSprites.register();
        ConnectionTextureSources.register(
                FusionConnectionTextureSourceProvider.INSTANCE);
        PreviewRuntime.register(
                FusionPreviewProvider.INSTANCE);
        ModelOwnershipRuntime.register(
                FabricFusionNativeModelOwnershipProvider.INSTANCE);
        EngineQueryRouter.registerNativeQueryOwnership(
                FabricFusionNativeQueryOwnership.INSTANCE);
        NativeExportRuntime.register(
                FusionNativeExportProvider.INSTANCE);
        PreparableModelLoadingPlugin.<Long>register(
                (state, executor) ->
                        CompletableFuture.completedFuture(
                                Long.valueOf(0)),
                (ignored, context) ->
                        context.modifyBlockModelAfterBake()
                                .register(
                                        ModelModifier.WRAP_LAST_PHASE,
                                        FabricFusionModelLifecycle
                                                ::wrap));
    }
}
