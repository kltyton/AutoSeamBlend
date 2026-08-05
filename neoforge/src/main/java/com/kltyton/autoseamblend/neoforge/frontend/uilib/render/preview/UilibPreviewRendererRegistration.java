package com.kltyton.autoseamblend.neoforge.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneRenderState;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * 中文：注册工作台专用的三维方块场景渲染器。
 *
 * English:
 * Registers the workbench-specific three-dimensional block-scene renderer.
 */
public final class UilibPreviewRendererRegistration {
    private UilibPreviewRendererRegistration() {}

    public static void register(
            RegisterPictureInPictureRenderersEvent event) {
        event.register(
                BlockSceneRenderState.class,
                BlockScenePictureInPictureRenderer::new);
    }
}
