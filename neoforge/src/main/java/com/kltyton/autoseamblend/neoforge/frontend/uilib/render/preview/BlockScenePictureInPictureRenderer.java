package com.kltyton.autoseamblend.neoforge.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneMath;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.Node;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneRenderState;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * 中文：在单个离屏颜色与深度目标中绘制整个可交互方块场景。
 *
 * English:
 * Renders the complete interactive block scene into one off-screen color and
 * depth target.
 */
public final class BlockScenePictureInPictureRenderer
        extends PictureInPictureRenderer<BlockSceneRenderState> {
    public BlockScenePictureInPictureRenderer(
            MultiBufferSource.BufferSource buffers) {
        super(buffers);
    }

    @Override
    public Class<BlockSceneRenderState>
            getRenderStateClass() {
        return BlockSceneRenderState.class;
    }

    @Override
    protected void renderToTexture(
            BlockSceneRenderState state,
            PoseStack pose) {
        Minecraft minecraft =
                Minecraft.getInstance();
        /*
         * 中文：左侧保留立体明暗；右侧单面投影使用原版平面物品光照，便于核对连接纹理。
         * English: The left pane keeps 3D shading, while the right face
         * projection uses vanilla flat-item lighting so connected textures
         * remain easy to inspect.
         */
        minecraft.gameRenderer
                .getLighting()
                .setupFor(
                        state.flatLighting()
                                ? Lighting.Entry.ITEMS_FLAT
                                : Lighting.Entry.ITEMS_3D);
        pose.translate(
                state.panX() / state.scale(),
                state.panY() / state.scale(),
                0.0F);
        pose.mulPose(PreviewSceneMath.cameraRotation(
                state.yaw(),
                state.pitch()));

        FeatureRenderDispatcher dispatcher =
                minecraft.gameRenderer
                        .getFeatureRenderDispatcher();
        SubmitNodeCollector collector =
                dispatcher.getSubmitNodeStorage();
        for (Node node
                : state.nodes()) {
            pose.pushPose();
            pose.translate(
                    node.x() - 0.5F,
                    node.y() - 0.5F,
                    node.z() - 0.5F);
            collector.submitMultiLayerBlockModel(
                    pose,
                    node.parts(),
                    node.translucent(),
                    node.tintLayers(),
                    LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    0);
            pose.popPose();
        }
        dispatcher.renderAllFeatures();
    }

    @Override
    protected float getTranslateY(
            int height,
            int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "AutoSeamBlend block scene";
    }
}
