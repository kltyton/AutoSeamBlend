package com.kltyton.autoseamblend.frontend.uilib.component.preview;

import com.daqem.uilib.api.widget.IWidget;
import com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneProjection;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneState;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneState.HoveredFace;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.RenderPort;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometry;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometryCache;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.jetbrains.annotations.NotNull;

/**
 * 中文：使用真实三维方块模型绘制可旋转、平移、缩放并可编辑邻居的预览场景。
 *
 * English:
 * Preview scene rendered with real three-dimensional block models and editable neighbors,
 * rotation, pan, and zoom.
 */
public final class InteractiveBlockPreviewWidget
        extends AbstractWidget
        implements IWidget {
    private static final int OUTLINE_PIXEL_SIZE = 1;
    private static final double OUTLINE_OCCLUSION_EPSILON =
            1.0E-5D;
    private static final int[][] CUBE_EDGES = {
        {0, 1}, {1, 3}, {3, 2}, {2, 0},
        {4, 5}, {5, 7}, {7, 6}, {6, 4},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private final PreviewSceneState state;
    private final Runnable changed;
    private final SceneGeometryCache geometryCache;
    private final RenderPort renderPort;
    private List<PreviewSceneProjection.ProjectedNode> projected =
            List.of();
    private long projectedSceneRevision =
            Long.MIN_VALUE;
    private long projectedCameraRevision =
            Long.MIN_VALUE;

    public InteractiveBlockPreviewWidget(
            int width,
            int height,
            PreviewSceneState state,
            SceneGeometryCache geometryCache,
            RenderPort renderPort,
            Runnable changed) {
        super(
                0,
                0,
                width,
                height,
                PreviewWidgetChrome.narration(false));
        this.state = Objects.requireNonNull(
                state,
                "state");
        this.geometryCache = Objects.requireNonNull(
                geometryCache,
                "geometryCache");
        this.renderPort = Objects.requireNonNull(
                renderPort,
                "renderPort");
        this.changed = Objects.requireNonNull(
                changed,
                "changed");
    }

    @Override
    protected void extractWidgetRenderState(
            @NotNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        int left = getX();
        int top = getY();
        PreviewWidgetChrome.drawCanvas(
                graphics,
                left,
                top,
                getWidth(),
                getHeight());
        graphics.enableScissor(
                left,
                top,
                left + getWidth(),
                top + getHeight());
        drawGrid(graphics);
        SceneGeometry geometry = geometryCache.geometry(state);
        renderPort.submit(
                graphics,
                geometry.renderRequest(
                        state,
                        left,
                        top,
                        left + getWidth(),
                        top + getHeight()));
        updateProjectedNodes();
        PreviewSceneProjection.Pick hovered = pick(
                mouseX,
                mouseY)
                .orElse(null);
        for (PreviewSceneProjection.ProjectedNode node : projected) {
            if (visible(node)) {
                drawNode(
                        graphics,
                        node,
                        hovered != null
                                && node == hovered.node());
            }
        }
        graphics.disableScissor();
        PreviewWidgetChrome.drawBorder(
                graphics,
                left,
                top,
                getWidth(),
                getHeight(),
                isFocused());
    }

    private void drawGrid(
            GuiGraphicsExtractor graphics) {
        int centerX = Math.round(
                getX()
                        + getWidth() / 2.0F
                        + state.panX());
        int centerY = Math.round(
                getY()
                        + getHeight() / 2.0F
                        + state.panY());
        int radius = Math.round(
                66.0F * state.zoom());
        graphics.horizontalLine(
                centerX - radius,
                centerX + radius,
                centerY,
                UilibWorkbenchTheme.BORDER_SUBTLE);
        graphics.verticalLine(
                centerX,
                centerY - radius / 2,
                centerY + radius / 2,
                UilibWorkbenchTheme.BORDER_SUBTLE);
    }

    private List<PreviewSceneProjection.ProjectedNode> projectNodes() {
        return PreviewSceneProjection.projectNodes(
                viewport(),
                camera(),
                38.0D,
                state.kind() != PreviewSceneState.Kind.PASSTHROUGH);
    }

    /**
     * 中文：相机和邻居均未变化时复用投影与物品图标，避免静止预览逐帧分配。
     *
     * English:
     * Reuses projections and item icons while camera and neighbors are unchanged,
     * avoiding per-frame allocation in a stationary preview.
     */
    private void updateProjectedNodes() {
        if (projectedSceneRevision
                        == state.sceneRevision()
                && projectedCameraRevision
                        == state.cameraRevision()) {
            return;
        }
        projected = projectNodes();
        projectedSceneRevision =
                state.sceneRevision();
        projectedCameraRevision =
                state.cameraRevision();
    }


    private void drawNode(
            GuiGraphicsExtractor graphics,
            PreviewSceneProjection.ProjectedNode node,
            boolean hovered) {
        if (hovered) {
            drawPlacementOutline(
                    graphics,
                    node);
        }
    }

    /**
     * 中文：投影完整方块线框，使旋转后的邻位仍与原版放置轮廓一样直观。
     *
     * English:
     * Projects a complete block wireframe so a rotated neighbor slot remains
     * as intuitive as a vanilla placement outline.
     */
    private void drawPlacementOutline(
            GuiGraphicsExtractor graphics,
            PreviewSceneProjection.ProjectedNode node) {
        List<PreviewSceneProjection.ProjectedPoint> corners = node.corners();
        Set<PreviewNeighborPosition> occupiedNeighbors =
                state.neighbors().keySet();
        for (int[] edge : CUBE_EDGES) {
            PreviewSceneProjection.ProjectedPoint start = corners.get(edge[0]);
            PreviewSceneProjection.ProjectedPoint end = corners.get(edge[1]);
            drawPixelLine(
                    graphics,
                    start,
                    end,
                    occupiedNeighbors);
        }
    }

    /**
     * 中文：逐像素裁掉被中心或已放置邻块遮挡的线段，避免轮廓穿透前景模型。
     *
     * English:
     * Clips each outline pixel against the center and placed neighbors so the
     * wireframe cannot show through foreground models.
     */
    private void drawPixelLine(
            GuiGraphicsExtractor graphics,
            PreviewSceneProjection.ProjectedPoint start,
            PreviewSceneProjection.ProjectedPoint end,
            Set<PreviewNeighborPosition> occupiedNeighbors) {
        double deltaX = end.x() - start.x();
        double deltaY = end.y() - start.y();
        int steps = Math.max(
                1,
                (int) Math.ceil(Math.max(
                        Math.abs(deltaX),
                        Math.abs(deltaY))));
        for (int step = 0; step <= steps; step++) {
            double progress = (double) step / steps;
            double sampleX = start.x()
                    + deltaX * progress;
            double sampleY = start.y()
                    + deltaY * progress;
            double sampleDepth = start.depth()
                    + (end.depth() - start.depth())
                            * progress;
            if (!occluded(
                    sampleX,
                    sampleY,
                    sampleDepth,
                    occupiedNeighbors)) {
                int pixelX = (int) Math.round(sampleX);
                int pixelY = (int) Math.round(sampleY);
                graphics.fill(
                        pixelX,
                        pixelY,
                        pixelX + OUTLINE_PIXEL_SIZE,
                        pixelY + OUTLINE_PIXEL_SIZE,
                        UilibWorkbenchTheme
                                .PREVIEW_BLOCK_OUTLINE);
            }
        }
    }

    private boolean occluded(
            double x,
            double y,
            double outlineDepth,
            Set<PreviewNeighborPosition> occupiedNeighbors) {
        for (PreviewSceneProjection.ProjectedNode candidate : projected) {
            if (candidate.neighbor().isPresent()
                    && !occupiedNeighbors.contains(
                            candidate.neighbor().orElseThrow())) {
                continue;
            }
            double candidateDepth = PreviewSceneProjection.frontDepthAt(
                    candidate,
                    x,
                    y);
            if (Double.isFinite(candidateDepth)
                    && candidateDepth
                            < outlineDepth
                                    - OUTLINE_OCCLUSION_EPSILON) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isValidClickButton(
            MouseButtonInfo button) {
        return PreviewPointerState.validButton(button.button());
    }

    /**
     * 中文：同步屏幕指针与最前方可见方块面，只在拾取结果改变时刷新精确面。
     *
     * English:
     * Synchronizes the screen pointer with the front-most visible block face
     * and refreshes the exact result only when that pick changes.
     */
    public void updateHoveredFace(
            double mouseX,
            double mouseY) {
        updateProjectedNodes();
        HoveredFace hovered = pick(
                mouseX,
                mouseY)
                .map(value -> new HoveredFace(
                        value.node().neighbor(),
                        value.face()))
                .orElse(null);
        if (state.setHoveredFace(hovered)) {
            changed.run();
        }
    }

    @Override
    public void onClick(
            MouseButtonEvent event,
            boolean doubleClick) {
        if (event.button() != PreviewPointerState.LEFT_BUTTON) {
            return;
        }
        pick(event.x(), event.y())
                .map(PreviewSceneProjection.Pick::node)
                .ifPresent(node -> {
                    boolean updated = node.neighbor()
                            .map(state::toggle)
                            .orElseGet(state::cycleCenter);
                    if (updated) {
                        changed.run();
                    }
                });
    }

    @Override
    protected void onDrag(
            MouseButtonEvent event,
            double deltaX,
            double deltaY) {
        if (event.button() == PreviewPointerState.MIDDLE_BUTTON) {
            state.rotate(deltaX, deltaY);
        } else if (event.button() == PreviewPointerState.RIGHT_BUTTON) {
            state.pan(deltaX, deltaY);
        }
        updateHoveredFace(
                event.x(),
                event.y());
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontal,
            double vertical) {
        if (!isMouseOver(mouseX, mouseY)
                || vertical == 0.0D) {
            return false;
        }
        state.zoom(vertical);
        updateHoveredFace(
                mouseX,
                mouseY);
        return true;
    }

    private Optional<PreviewSceneProjection.Pick> pick(
            double mouseX,
            double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return Optional.empty();
        }
        return PreviewSceneProjection.pick(
                projected,
                mouseX,
                mouseY,
                viewport());
    }

    /**
     * 中文：只有完全位于预览画布内的邻接控件才可见且可交互，避免覆盖工作台其他控件。
     *
     * English:
     * Neighbor controls are visible and interactive only while fully contained
     * by the preview canvas, preventing them from covering the rest of the
     * workbench.
     */
    private boolean visible(
            PreviewSceneProjection.ProjectedNode node) {
        return PreviewSceneProjection.visible(node, viewport());
    }

    @Override
    protected void updateWidgetNarration(
            @NotNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private PreviewViewModel.Viewport viewport() {
        return new PreviewViewModel.Viewport(
                getX(),
                getY(),
                Math.max(1, getWidth()),
                Math.max(1, getHeight()));
    }

    private PreviewViewModel.Camera camera() {
        return state.camera();
    }
}
