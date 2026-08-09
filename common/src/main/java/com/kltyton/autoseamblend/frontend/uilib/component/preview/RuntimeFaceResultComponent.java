package com.kltyton.autoseamblend.frontend.uilib.component.preview;

import com.kltyton.autoseamblend.frontend.uilib.component.AbstractAbsoluteComponent;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneMath;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneState;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.Node;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.RenderRequest;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.RenderPort;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometry;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometryCache;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 中文：沿当前悬停面正交投影左侧同一份运行时方块模型场景，并在右侧放大显示。
 *
 * English:
 * Enlarges an orthographic projection of the same runtime block-model scene
 * used by the left pane, aligned to the currently hovered face.
 */
public final class RuntimeFaceResultComponent
        extends AbstractAbsoluteComponent<RuntimeFaceResultComponent> {
    private static final int LABEL_GAP = 4;
    private static final int CANVAS_PADDING = 8;
    private static final int MIN_PROJECTION_SIZE = 48;

    private final PreviewSceneState scene;
    private final SceneGeometryCache geometryCache;
    private final RenderPort renderPort;
    private Direction face;

    public RuntimeFaceResultComponent(
            int width,
            int height,
            PreviewSceneState scene,
            SceneGeometryCache geometryCache,
            RenderPort renderPort) {
        super(0, 0, width, height);
        this.scene = Objects.requireNonNull(
                scene,
                "scene");
        this.geometryCache = Objects.requireNonNull(
                geometryCache,
                "geometryCache");
        this.renderPort = Objects.requireNonNull(
                renderPort,
                "renderPort");
    }

    public static boolean fits(
            int width,
            int height,
            int fontLineHeight) {
        return width >= MIN_PROJECTION_SIZE
                && height >= MIN_PROJECTION_SIZE
                        + fontLineHeight
                        + LABEL_GAP;
    }

    public void setFace(
            Direction value) {
        face = value;
    }

    @Override
    protected void extractRenderState(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        if (face == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int labelHeight = font.lineHeight + LABEL_GAP;
        int canvasLeft = getTotalX();
        int canvasTop = getTotalY() + labelHeight;
        int canvasWidth = getWidth();
        int canvasHeight = getHeight() - labelHeight;
        drawLabel(
                graphics,
                font,
                canvasLeft,
                getTotalY(),
                canvasWidth);
        graphics.fill(
                canvasLeft,
                canvasTop,
                canvasLeft + canvasWidth,
                canvasTop + canvasHeight,
                UilibWorkbenchTheme.SURFACE_INPUT);
        graphics.enableScissor(
                canvasLeft,
                canvasTop,
                canvasLeft + canvasWidth,
                canvasTop + canvasHeight);
        SceneGeometry geometry = geometryCache.geometry(scene);
        Projection projection = Projection.fit(
                geometry.nodes(),
                face,
                canvasWidth,
                canvasHeight);
        renderPort.submit(
                graphics,
                new RenderRequest(
                        geometry.nodes(),
                        projection.yaw(),
                        projection.pitch(),
                        projection.panX(),
                        projection.panY(),
                        canvasLeft,
                        canvasTop,
                        canvasLeft + canvasWidth,
                        canvasTop + canvasHeight,
                        projection.scale(),
                        true));
        graphics.disableScissor();
        drawBorder(
                graphics,
                canvasLeft,
                canvasTop,
                canvasWidth,
                canvasHeight,
                UilibWorkbenchTheme.FOCUS_RING);
    }

    private void drawLabel(
            GuiGraphics graphics,
            Font font,
            int left,
            int top,
            int width) {
        Component direction = Component.translatable(
                "gui.autoseamblend.preview.face."
                        + face.getName());
        FormattedText label = Component.translatable(
                "gui.autoseamblend.preview.view.current",
                direction);
        FormattedText clipped = font.width(label) <= width
                ? label
                : font.substrByWidth(label, width);
        FormattedCharSequence visual =
                Language.getInstance()
                        .getVisualOrder(clipped);
        graphics.drawString(
                font,
                visual,
                left + Math.max(
                        0,
                        (width - font.width(visual)) / 2),
                top,
                UilibWorkbenchTheme.TEXT_PRIMARY,
                false);
    }

    private static void drawBorder(
            GuiGraphics graphics,
            int left,
            int top,
            int width,
            int height,
            int color) {
        int right = left + width - 1;
        int bottom = top + height - 1;
        graphics.hLine(left, right, top, color);
        graphics.hLine(left, right, bottom, color);
        graphics.vLine(left, top, bottom, color);
        graphics.vLine(right, top, bottom, color);
    }

    /**
     * 中文：以方块包围盒计算自适应缩放；真实模型 Quad 仍由共享 PIP 深度管线负责绘制。
     *
     * English:
     * Fits block bounds to the viewport while the shared PIP depth pipeline
     * remains responsible for rendering the actual model quads.
     */
    private record Projection(
            float yaw,
            float pitch,
            float panX,
            float panY,
            float scale) {
        private static Projection fit(
                List<Node> nodes,
                Direction face,
                int width,
                int height) {
            ViewAngles angles = ViewAngles.forFace(face);
            Quaternionf rotation =
                    PreviewSceneMath.cameraRotation(
                            angles.yaw(),
                            angles.pitch());
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (Node node : nodes) {
                for (int x = 0; x <= 1; x++) {
                    for (int y = 0; y <= 1; y++) {
                        for (int z = 0; z <= 1; z++) {
                            Vector3f projected = rotation.transform(
                                    node.x() + x - 0.5F,
                                    node.y() + y - 0.5F,
                                    node.z() + z - 0.5F,
                                    new Vector3f());
                            minX = Math.min(minX, projected.x);
                            minY = Math.min(minY, projected.y);
                            maxX = Math.max(maxX, projected.x);
                            maxY = Math.max(maxY, projected.y);
                        }
                    }
                }
            }
            float spanX = Math.max(1.0F, maxX - minX);
            float spanY = Math.max(1.0F, maxY - minY);
            float availableWidth = Math.max(
                    1.0F,
                    width - CANVAS_PADDING * 2.0F);
            float availableHeight = Math.max(
                    1.0F,
                    height - CANVAS_PADDING * 2.0F);
            float scale = Math.max(
                    1.0F,
                    Math.min(
                            availableWidth / spanX,
                            availableHeight / spanY));
            return new Projection(
                    angles.yaw(),
                    angles.pitch(),
                    -(minX + maxX) * 0.5F * scale,
                    -(minY + maxY) * 0.5F * scale,
                    scale);
        }
    }

    /** 中文：把原版六个面映射到外侧正视相机。 / English: Maps each vanilla face to an outside-facing camera. */
    private record ViewAngles(
            float yaw,
            float pitch) {
        private static ViewAngles forFace(
                Direction face) {
            return switch (face) {
                case NORTH -> new ViewAngles(0.0F, 0.0F);
                case SOUTH -> new ViewAngles(180.0F, 0.0F);
                case EAST -> new ViewAngles(90.0F, 0.0F);
                case WEST -> new ViewAngles(-90.0F, 0.0F);
                case UP -> new ViewAngles(180.0F, -90.0F);
                case DOWN -> new ViewAngles(0.0F, 90.0F);
                default -> throw new IllegalStateException(
                        "unsupported preview face: " + face);
            };
        }
    }
}
