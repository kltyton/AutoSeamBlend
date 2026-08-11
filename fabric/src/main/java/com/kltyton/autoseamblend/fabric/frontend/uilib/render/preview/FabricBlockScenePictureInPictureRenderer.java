package com.kltyton.autoseamblend.fabric.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGuiRenderer;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneRenderState;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 中文：Fabric 薄门面：保留既有提交入口与类名，完整渲染算法（与 NeoForge 同一份已
 * 人工验收的深度修复语义）位于 common 的 BlockSceneGuiRenderer；本类不含渲染逻辑，
 * 禁止复制算法。
 *
 * English: Fabric thin facade: keeps the existing submission entry and class
 * name; the full render algorithm (the same manually accepted depth-fix semantics
 * as NeoForge) lives in the common BlockSceneGuiRenderer. This class holds no
 * render logic and the algorithm must not be duplicated here.
 */
public final class FabricBlockScenePictureInPictureRenderer {
    private FabricBlockScenePictureInPictureRenderer() {}

    public static void render(
            GuiGraphics graphics,
            BlockSceneRenderState state) {
        BlockSceneGuiRenderer.render(
                graphics,
                state);
    }
}
