package com.kltyton.autoseamblend.neoforge.compat.athena.bootstrap;

import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.compat.athena.authoring.export.AthenaNativeExportProvider;
import com.kltyton.autoseamblend.compat.athena.authoring.materialize.AthenaConnectionTextureSourceProvider;
import com.kltyton.autoseamblend.neoforge.compat.athena.runtime.AthenaModelLifecycle;
import com.kltyton.autoseamblend.neoforge.compat.athena.runtime.AthenaNativeModelOwnershipProvider;
import com.kltyton.autoseamblend.neoforge.compat.athena.runtime.AthenaPreviewProvider;
import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
import com.kltyton.autoseamblend.authoring.preview.PreviewRuntime;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import net.neoforged.bus.api.IEventBus;

/**
 * 中文：仅在不依赖第三方类型的发现流程选中 Athena 后，加载链接 Athena 的运行时。
 *
 * English:
 * Loads the Athena-linked runtime only after third-party-free discovery selected Athena.
 */
public final class AthenaRuntimeBootstrap {
    private AthenaRuntimeBootstrap() {}

    public static void register(IEventBus modEventBus) {
        AthenaGeneratedStateSprites.register();
        ConnectionTextureSources.register(
                AthenaConnectionTextureSourceProvider.INSTANCE);
        PreviewRuntime.register(
                AthenaPreviewProvider.INSTANCE);
        ModelOwnershipRuntime.register(
                AthenaNativeModelOwnershipProvider.INSTANCE);
        EngineQueryRouter.registerNativeQueryOwnership(
                AthenaNativeModelOwnershipProvider.INSTANCE);
        NativeExportRuntime.register(
                AthenaNativeExportProvider.INSTANCE);
        modEventBus.addListener(AthenaModelLifecycle::onModifyBakingResult);
    }
}
