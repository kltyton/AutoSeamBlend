package com.kltyton.autoseamblend.forge.compat.fusion.bootstrap;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionModifierLifecycleHooks;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionMutableQuadHooks;
import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.compat.fusion.authoring.export.FusionNativeExportProvider;
import com.kltyton.autoseamblend.compat.fusion.authoring.materialize.FusionConnectionTextureSourceProvider;
import com.kltyton.autoseamblend.forge.compat.fusion.runtime.FusionNativeModelOwnershipProvider;
import com.kltyton.autoseamblend.forge.compat.fusion.runtime.FusionNativeQueryOwnership;
import com.kltyton.autoseamblend.forge.compat.fusion.runtime.ForgeFusionModifierLifecycleHooks;
import com.kltyton.autoseamblend.forge.compat.fusion.runtime.ForgeFusionMutableQuadHooks;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionPreviewProvider;
import com.kltyton.autoseamblend.compat.fusion.runtime.texture.FusionGeneratedStateSprites;
import com.kltyton.autoseamblend.authoring.preview.PreviewRuntime;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 中文：仅在不依赖第三方类型的发现流程选中 Fusion 后加载其链接运行时。
 *
 * English:
 * Loads the Fusion-linked runtime only after third-party-free discovery selected Fusion.
 */
public final class FusionRuntimeBootstrap {
    private FusionRuntimeBootstrap() {}

    public static void register(IEventBus modEventBus) {
        FusionModifierLifecycleHooks.install(ForgeFusionModifierLifecycleHooks.INSTANCE);
        FusionMutableQuadHooks.install(ForgeFusionMutableQuadHooks.INSTANCE);
        FusionGeneratedStateSprites.register();
        ConnectionTextureSources.register(
                FusionConnectionTextureSourceProvider.INSTANCE);
        PreviewRuntime.register(
                FusionPreviewProvider.INSTANCE);
        ModelOwnershipRuntime.register(
                FusionNativeModelOwnershipProvider.INSTANCE);
        EngineQueryRouter.registerNativeQueryOwnership(
                FusionNativeQueryOwnership.INSTANCE);
        NativeExportRuntime.register(
                FusionNativeExportProvider.INSTANCE);
        // 中文：1.20.1 Forge Fusion 不在 ModelEvent 中应用 modifier；模型捕获与最终
        // 包装由 ForgeFusionModifierLifecycleHooks 在原生 publish 之后完成。
        // English: 1.20.1 Forge Fusion does not apply modifiers in ModelEvent; native
        // capture and final wrapping run from ForgeFusionModifierLifecycleHooks after publish.
    }
}
