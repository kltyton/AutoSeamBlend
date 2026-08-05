package com.kltyton.autoseamblend.fabric.compat.athena.bootstrap;

import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.authoring.preview.PreviewRuntime;
import com.kltyton.autoseamblend.compat.athena.authoring.export.AthenaNativeExportProvider;
import com.kltyton.autoseamblend.compat.athena.authoring.materialize.AthenaConnectionTextureSourceProvider;
import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.fabric.compat.athena.runtime.FabricAthenaModelLifecycle;
import com.kltyton.autoseamblend.fabric.compat.athena.runtime.FabricAthenaNativeModelOwnershipProvider;
import com.kltyton.autoseamblend.fabric.compat.athena.runtime.FabricAthenaPreviewProvider;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricEngineBootstrap;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;

/**
 * 中文：仅在不依赖第三方类型的发现流程选中 Athena 后，加载链接 Athena 的运行时。
 *
 * English: Loads the Athena-linked runtime only after third-party-free
 * discovery selected Athena.
 */
public enum AthenaRuntimeBootstrap
        implements FabricEngineBootstrap {
    INSTANCE;

    @Override
    public void register() {
        AthenaGeneratedStateSprites.register();
        ConnectionTextureSources.register(
                AthenaConnectionTextureSourceProvider.INSTANCE);
        PreviewRuntime.register(
                FabricAthenaPreviewProvider.INSTANCE);
        ModelOwnershipRuntime.register(
                FabricAthenaNativeModelOwnershipProvider.INSTANCE);
        EngineQueryRouter.registerNativeQueryOwnership(
                FabricAthenaNativeModelOwnershipProvider.INSTANCE);
        NativeExportRuntime.register(
                AthenaNativeExportProvider.INSTANCE);
        PreparableModelLoadingPlugin.<Long>register(
                (state, executor) ->
                        CompletableFuture.completedFuture(
                                Long.valueOf(0)),
                (ignored, context) ->
                        context.modifyBlockModelAfterBake()
                                .register(
                                        ModelModifier.WRAP_LAST_PHASE,
                                        FabricAthenaModelLifecycle
                                                ::wrap));
    }
}
