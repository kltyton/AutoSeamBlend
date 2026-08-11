package com.kltyton.autoseamblend.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneMath;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.Node;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneRenderState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 中文：两个 Loader 共用的完整方块场景 GUI 渲染实现，承载 1.21.1 NeoForge 已人工
 * 验收的深度根因修复语义。在 GUI 绘制通道内直接提交可交互方块场景；方块贴图层的深度
 * 测试保持节点前后遮挡，shade=false 提供右侧平面光照、true 保留烘焙明暗。
 * NeoForge/Fabric 的 submit/registration 端口只保留 Loader 差异，两端都委托本实现；
 * 禁止在 Loader 包复制本算法。
 *
 * English: Full block-scene GUI renderer shared by both loaders, carrying the
 * manually accepted 1.21.1 NeoForge depth-fix semantics. It submits the
 * interactive block scene directly in the GUI pass; block-sheet depth testing
 * keeps node occlusion, while shade=false gives the flat right-pane lighting and
 * shade=true keeps baked shading. The NeoForge/Fabric submit/registration ports
 * keep only Loader-specific differences and both delegate here; the algorithm
 * must not be copied into Loader packages.
 *
 * 中文：两端 Loader 统一使用本实现，提交语义与已验收的 1.21.1 NeoForge 一致：
 * 使用 GUI 默认投影，pose = T(rectCenter+pan)·S(scale,scale,1)·R(yaw,pitch)，
 * z 置于 GUI ortho 区间内（SCENE_Z=2000），配合原版 block-sheet RenderType
 * （原生 cull/depth/translucent 语义）。不改 modelview、不用负缩放；仅对 3D 场景
 * （flatLighting=false）在预览 scissor 内清深度，并临时取反 clip-space Z（JOML
 * ortho 的 m22/m32），使窗口深度方向与共享投影/26.1.2 一致（更小旋转后 z = 更近），
 * finally 恢复投影与 VertexSorting。窗口空间绕序不变，entityCutout 的 CULL 不受
 * 影响；自定义 ortho 迁移会引入行列式为负的镜像变换，在 entityCutout 开启 CULL 时
 * 把正面绕序翻转导致材质不可见，故不可取。
 *
 * English: Both loaders use this single implementation with the accepted 1.21.1
 * NeoForge submission semantics: the GUI default projection with pose
 * T(rectCenter+pan)·S(scale,scale,1)·R(yaw,pitch), z placed inside the GUI ortho
 * range (SCENE_Z=2000), using the vanilla block-sheet RenderTypes (native
 * cull/depth/translucent semantics). No modelview change and no negative scale;
 * only the 3D scene (flatLighting=false) clears depth inside the preview scissor
 * and temporarily negates clip-space Z (JOML ortho m22/m32) so window-depth order
 * matches the shared projection / accepted 26.1.2 (smaller rotated z = nearer),
 * restoring projection and VertexSorting in finally. Window-space winding is
 * unchanged, so entityCutout CULL is unaffected; a custom ortho migration would
 * introduce a negative-determinant mirror that flips front-face winding under
 * entityCutout culling, making the material invisible, and is therefore avoided.
 *
 * 中文：1.20.1 方块贴图着色器（cutout/translucent）只使用顶点色与光照贴图采样；
 * Lighting.setupForFlatItems/setupFor3DItems 只设置实体/物品着色器的漫反射方向，
 * 对该 RenderType 无效果，因此这里不调用 Lighting API。亮度完全由 putBulkData 的
 * light/brightness/shade 参数决定（FULL_BRIGHT light + 烘焙顶点色 + shade），
 * tint 与 ARGB 原样保留。
 *
 * English: 1.20.1 block-sheet shaders (cutout/translucent) consume only the
 * vertex color and the lightmap sample; Lighting.setupForFlatItems/
 * setupFor3DItems only set diffuse directions for entity/item shaders and have
 * no effect on these render types, so no Lighting API is called here. Brightness
 * is fully determined by the putBulkData light/brightness/shade parameters
 * (FULL_BRIGHT light plus the baked vertex color and shade flag), while tint and
 * ARGB stay untouched.
 */
public final class BlockSceneGuiRenderer {
    private static final float SCENE_Z = 2000.0F;
    private static final float[] FLAT_BRIGHTNESS = {
        1.0F, 1.0F, 1.0F, 1.0F
    };
    private static final int[] FULL_BRIGHT_LIGHT = {
        LightTexture.FULL_BRIGHT,
        LightTexture.FULL_BRIGHT,
        LightTexture.FULL_BRIGHT,
        LightTexture.FULL_BRIGHT
    };

    private BlockSceneGuiRenderer() {}

    public static void render(
            GuiGraphics graphics,
            BlockSceneRenderState state) {
        // 中文：优先使用渲染状态自带的裁剪区，为空时回退到预览 bounds（记录构造保证
        // 非 null），确保 3D 深度翻转不会因裁剪区缺失而静默失效。
        // English: Prefer the render state's own scissor and fall back to the preview
        // bounds (guaranteed non-null by the record constructor) so the 3D depth flip
        // cannot silently no-op when a scissor is missing.
        ScreenRectangle scissor = state.scissorArea() != null
                ? state.scissorArea()
                : state.bounds();
        boolean depthFlipped = !state.flatLighting();
        if (depthFlipped) {
            // 中文：先把此前 GUI 批次（面板、画布底色、十字线）在原始投影下提交；
            // 否则旧方向深度留在预览区，反转投影后的场景深度约 0.6 会被 LEQUAL 以
            // 0.6>0.5 拒绝，导致完全不渲染。
            // English: Flush earlier GUI batches (panels, canvas fill, crosshair)
            // under the original projection first; otherwise their old-direction
            // depth stays in the preview region and the flipped scene (~0.6) is
            // rejected by LEQUAL (0.6 > 0.5), making it completely invisible.
            graphics.flush();
        }
        if (scissor != null) {
            graphics.enableScissor(
                    scissor.left(),
                    scissor.top(),
                    scissor.right(),
                    scissor.bottom());
        }
        Matrix4f originalProjection = null;
        VertexSorting originalSorting = null;
        try {
            if (depthFlipped) {
                // 中文：仅清预览 scissor 内的深度（256=GL_DEPTH_BUFFER_BIT），把面板等
                // 旧批次写入的 0.5 重置回 far；clear depth 值保持/恢复为 1.0。
                // English: Clear depth only inside the preview scissor
                // (256 = GL_DEPTH_BUFFER_BIT), resetting the 0.5 written by earlier
                // panels back to far; the clear-depth value stays/returns to 1.0.
                RenderSystem.clearDepth(1.0D);
                RenderSystem.clear(256, Minecraft.ON_OSX);
                RenderSystem.clearDepth(1.0D);
                // 中文：备份并在本场景绘制期间临时取反 clip-space Z（JOML ortho 中 z
                // 系数 m22 与 z 平移 m32），精确保留 X/Y/near/far；不改 modelview、
                // 不用负缩放。窗口深度方向由此与 PreviewSceneProjection/26.1.2 一致
                // （更小旋转后 z = 更近），且窗口空间绕序不变，CULL 仍按原正面可见。
                // VertexSorting 用 byDistance(z)：较大旋转后 z（较远）先提交，满足
                // translucent 远→近语义。
                // English: Backup and temporarily negate clip-space Z (m22 z-scale and
                // m32 z-translation of the JOML ortho) for this scene, preserving
                // X/Y/near/far exactly; no modelview change, no negative scale. Window
                // depth order now agrees with PreviewSceneProjection / accepted 26.1.2
                // (smaller rotated z = nearer), and window-space winding is unchanged
                // so CULL keeps the original front faces. VertexSorting uses
                // byDistance(z): larger rotated z (farther) is submitted first,
                // preserving the far-to-near semantics for translucent.
                originalProjection = new Matrix4f(
                        RenderSystem.getProjectionMatrix());
                originalSorting = RenderSystem.getVertexSorting();
                Matrix4f flippedProjection = new Matrix4f(originalProjection)
                        .m22(-originalProjection.m22())
                        .m32(-originalProjection.m32());
                RenderSystem.setProjectionMatrix(
                        flippedProjection,
                        VertexSorting.byDistance(point -> point.z()));
            }
            PoseStack pose = graphics.pose();
            pose.pushPose();
            // 中文：与两端一致的矩阵顺序：中心+pan 平移 → scale(scale,scale,1) →
            // 相机旋转；后续 drawNode 再叠加 node-0.5。
            // English: Matrix order shared by both loaders: center+pan translate,
            // scale(scale,scale,1), camera rotation; drawNode then adds node-0.5.
            pose.translate(
                    (state.x0() + state.x1()) / 2.0F
                            + state.panX(),
                    (state.y0() + state.y1()) / 2.0F
                            + state.panY(),
                    SCENE_Z);
            pose.scale(
                    state.scale(),
                    state.scale(),
                    1.0F);
            pose.mulPose(PreviewSceneMath.cameraRotation(
                    state.yaw(),
                    state.pitch()));
            List<Node> opaque = state.nodes().stream()
                    .filter(node -> !node.translucent())
                    .sorted(Comparator.comparingDouble(
                            node -> -viewDepth(node, state)))
                    .toList();
            List<Node> translucent = state.nodes().stream()
                    .filter(Node::translucent)
                    .sorted(Comparator.comparingDouble(
                            node -> viewDepth(node, state)))
                    .toList();
            for (Node node : opaque) {
                drawNode(
                        graphics,
                        pose,
                        node,
                        state.flatLighting());
            }
            for (Node node : translucent) {
                drawNode(
                        graphics,
                        pose,
                        node,
                        state.flatLighting());
            }
            pose.popPose();
            // 中文：必须在临时投影下提交批次，否则深度方向又回到原投影。
            // English: The batch must be submitted under the temporary projection,
            // otherwise the depth direction reverts to the original projection.
            graphics.bufferSource().endBatch();
        } finally {
            // 中文：无论绘制是否异常，都恢复投影/排序并解除裁剪，避免污染后续 GUI；
            // 仅在确实完成备份后恢复，防止 finally 自身因空值再抛异常。
            // English: Restore projection/sorting and release the scissor no matter
            // how drawing ends, so later GUI batches stay unaffected; restore only
            // after the backup actually succeeded, so the finally block itself cannot
            // throw on null state.
            if (depthFlipped && originalProjection != null) {
                RenderSystem.setProjectionMatrix(
                        originalProjection,
                        originalSorting);
            }
            if (scissor != null) {
                graphics.disableScissor();
            }
        }
    }

    private static void drawNode(
            GuiGraphics graphics,
            PoseStack pose,
            Node node,
            boolean flatLighting) {
        RenderType renderType =
                node.translucent()
                        ? Sheets.translucentCullBlockSheet()
                        : Sheets.cutoutBlockSheet();
        VertexConsumer consumer =
                graphics.bufferSource()
                        .getBuffer(renderType);
        pose.pushPose();
        pose.translate(
                node.x() - 0.5F,
                node.y() - 0.5F,
                node.z() - 0.5F);
        for (BakedQuad quad : node.quads()) {
            draw(
                    consumer,
                    pose,
                    quad,
                    node.tintLayers(),
                    flatLighting);
        }
        pose.popPose();
    }

    private static void draw(
            VertexConsumer consumer,
            PoseStack pose,
            BakedQuad quad,
            int[] tintLayers,
            boolean flatLighting) {
        float red = 1.0F;
        float green = 1.0F;
        float blue = 1.0F;
        if (quad.isTinted()) {
            int tintIndex = quad.getTintIndex();
            if (tintIndex >= 0
                    && tintIndex < tintLayers.length) {
                int tint = tintLayers[tintIndex];
                red = ((tint >> 16) & 0xFF) / 255.0F;
                green = ((tint >> 8) & 0xFF) / 255.0F;
                blue = (tint & 0xFF) / 255.0F;
            }
        }
        consumer.putBulkData(
                pose.last(),
                quad,
                FLAT_BRIGHTNESS,
                red,
                green,
                blue,
                FULL_BRIGHT_LIGHT,
                OverlayTexture.NO_OVERLAY,
                !flatLighting && quad.isShade());
    }

    private static float viewDepth(
            Node node,
            BlockSceneRenderState state) {
        return PreviewSceneMath.cameraRotation(
                        state.yaw(),
                        state.pitch())
                .transform(
                        node.x(),
                        node.y(),
                        node.z(),
                        new Vector3f())
                .z();
    }
}
