package com.kltyton.autoseamblend.fabric.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneMath;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.Node;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneRenderState;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;

/**
 * 中文：在单个离屏颜色与深度目标中绘制整个可交互方块场景。
 *
 * <p>English: Renders the complete interactive block scene into one off-screen
 * color and depth target. Vanilla has no per-quad multi-layer submission, so
 * each node uses the vanilla block-model submission with the cutout or
 * translucent sheet selected by the node's translucency flag.
 */
public final class FabricBlockScenePictureInPictureRenderer
        extends PictureInPictureRenderer<BlockSceneRenderState> {
    public FabricBlockScenePictureInPictureRenderer(
            PictureInPictureRendererRegistry.Context context) {
        super(context.bufferSource());
    }

    public static void register() {
        PictureInPictureRendererRegistry.register(
                FabricBlockScenePictureInPictureRenderer::new);
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
        Minecraft minecraft = Minecraft.getInstance();
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
        QuadInstance instance = new QuadInstance();
        for (Node node : state.nodes()) {
            RenderType renderType =
                    node.translucent()
                            ? Sheets.translucentBlockSheet()
                            : Sheets.cutoutBlockSheet();
            VertexConsumer consumer =
                    bufferSource.getBuffer(renderType);
            pose.pushPose();
            pose.translate(
                    node.x() - 0.5F,
                    node.y() - 0.5F,
                    node.z() - 0.5F);
            for (BlockStateModelPart part
                    : node.parts()) {
                for (Direction direction
                        : Direction.values()) {
                    for (BakedQuad quad
                            : part.getQuads(direction)) {
                        draw(
                                consumer,
                                pose,
                                quad,
                                instance,
                                node.tintLayers());
                    }
                }
                for (BakedQuad quad
                        : part.getQuads(null)) {
                    draw(
                            consumer,
                            pose,
                            quad,
                            instance,
                            node.tintLayers());
                }
            }
            pose.popPose();
        }
        bufferSource.endBatch();
    }

    private static void draw(
            VertexConsumer consumer,
            PoseStack pose,
            BakedQuad quad,
            QuadInstance instance,
            int[] tintLayers) {
        instance.setColor(0xFFFFFFFF);
        instance.setLightCoords(
                LightCoordsUtil.FULL_BRIGHT);
        instance.setOverlayCoords(
                OverlayTexture.NO_OVERLAY);
        BakedQuad.MaterialInfo material =
                quad.materialInfo();
        if (material.isTinted()) {
            int tintIndex = material.tintIndex();
            if (tintIndex >= 0
                    && tintIndex < tintLayers.length) {
                instance.setColor(
                        tintLayers[tintIndex]);
            }
        }
        consumer.putBakedQuad(
                pose.last(),
                quad,
                instance);
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
