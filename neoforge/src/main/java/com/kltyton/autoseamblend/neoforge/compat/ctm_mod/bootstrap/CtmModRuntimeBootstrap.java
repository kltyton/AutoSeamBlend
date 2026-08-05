package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.bootstrap;

import com.kltyton.autoseamblend.authoring.export.NativeExportRuntime;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.export.CtmModNativeExportProvider;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.materialize.CtmModConnectionTextureSourceProvider;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.CtmModOverlayStateSampler;
import com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime.CtmModPreviewProvider;
import com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime.CtmModModelLifecycle;
import com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime.CtmModNativeModelOwnershipProvider;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModGeneratedStateSprites;
import com.kltyton.autoseamblend.authoring.preview.PreviewRuntime;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import net.neoforged.bus.api.IEventBus;

/** 中文：仅在不依赖第三方类型的发现流程找到 CTM Lib 后加载其链接运行时。 / English: Loads the CTM Lib-linked runtime only after third-party-free discovery found it. */
public final class CtmModRuntimeBootstrap {
    private CtmModRuntimeBootstrap() {}

    public static void register(IEventBus modEventBus) {
        // 中文：NeoForge 方块外观是 Loader 专属 API，作为钩子注入公共 CTM overlay 采样。
        // English: NeoForge block appearance is a Loader-exclusive API and joins
        // the shared CTM overlay sampling as an injected hook.
        CtmModOverlayStateSampler.installAppearanceResolver(
                (candidate, level, candidatePos, face, receiver, origin) ->
                        candidate.getAppearance(
                                level,
                                candidatePos,
                                face,
                                receiver,
                                origin));
        CtmModGeneratedStateSprites.register();
        ConnectionTextureSources.register(
                CtmModConnectionTextureSourceProvider.INSTANCE);
        PreviewRuntime.register(
                CtmModPreviewProvider.INSTANCE);
        ModelOwnershipRuntime.register(
                CtmModNativeModelOwnershipProvider.INSTANCE);
        EngineQueryRouter.registerNativeQueryOwnership(
                CtmModNativeModelOwnershipProvider.INSTANCE);
        NativeExportRuntime.register(
                CtmModNativeExportProvider.INSTANCE);
        modEventBus.addListener(
                CtmModModelLifecycle::onModifyBakingResult);
    }
}
