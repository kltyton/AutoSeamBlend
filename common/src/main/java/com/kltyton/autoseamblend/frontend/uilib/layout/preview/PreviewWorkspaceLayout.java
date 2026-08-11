package com.kltyton.autoseamblend.frontend.uilib.layout.preview;

import com.daqem.uilib.client.gui.component.TextComponent;
import com.daqem.uilib.client.gui.text.Text;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import com.kltyton.autoseamblend.frontend.uilib.component.PanelComponent;
import com.kltyton.autoseamblend.frontend.uilib.component.preview.InteractiveBlockPreviewWidget;
import com.kltyton.autoseamblend.frontend.uilib.component.preview.RuntimeFaceResultComponent;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchLayoutHost;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneState;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.RenderPort;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometryCache;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * 中文：计算并装配预览模式的交互场景与 Runtime 单面正交投影，窄窗口改为上下分区。
 *
 * English:
 * Computes and assembles the interactive scene and Runtime face-projection
 * regions, stacking them vertically in narrow windows.
 */
public final class PreviewWorkspaceLayout {
    private static final int GRID =
            UilibWorkbenchMetrics.GRID;
    private static final int GAP =
            UilibWorkbenchMetrics.PANEL_GAP;
    private static final int PANEL_INSET =
            UilibWorkbenchMetrics.PANEL_GAP;
    private static final int STACK_THRESHOLD =
            UilibWorkbenchMetrics.NARROW_WIDTH;
    private static final int SCENE_CAPTION_HEIGHT = GRID * 6;
    private static final int MIN_SCENE_CANVAS_WIDTH = GRID * 16;
    private static final int MIN_SCENE_CANVAS_HEIGHT = GRID * 12;
    private static final int MIN_SCENE_REGION_HEIGHT =
            MIN_SCENE_CANVAS_HEIGHT
                    + PANEL_INSET * 2
                    + SCENE_CAPTION_HEIGHT;
    private static final int FACE_CONTENT_TOP = GRID * 8;
    private static final int MIN_FACE_REGION_HEIGHT =
            FACE_CONTENT_TOP + GRID * 16 + PANEL_INSET;
    private static final int MIN_FACE_REGION_WIDTH = GRID * 18;
    private static final int MAX_FACE_REGION_WIDTH = GRID * 46;

    private final WorkbenchLayoutHost host;
    private final SceneGeometryCache geometryCache;
    private final RenderPort renderPort;
    private final TextComponent availability =
            new TextComponent(
                    0,
                    0,
                    new Text(
                            Minecraft.getInstance().font,
                            Component.empty()));
    private Component availabilityText = Component.empty();
    private InteractiveBlockPreviewWidget sceneWidget;
    private RuntimeFaceResultComponent faceResult;
    private Direction face;
    private boolean faceSpaceAvailable;

    public PreviewWorkspaceLayout(
            WorkbenchLayoutHost host,
            SceneGeometryCache geometryCache,
            RenderPort renderPort) {
        this.host = Objects.requireNonNull(host, "host");
        this.geometryCache = Objects.requireNonNull(
                geometryCache,
                "geometryCache");
        this.renderPort = Objects.requireNonNull(
                renderPort,
                "renderPort");
    }

    public void assemble(
            PreviewSceneState scene,
            Direction currentFace,
            Runnable changed,
            int left,
            int top,
            int width,
            int height) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(changed, "changed");
        face = currentFace;
        Regions regions = Regions.within(
                left,
                top,
                width,
                height);
        assembleScene(
                scene,
                geometryCache,
                changed,
                regions.scene());
        assembleFace(
                scene,
                geometryCache,
                regions.face());
    }

    public InteractiveBlockPreviewWidget sceneWidget() {
        return sceneWidget;
    }

    public void clear() {
        sceneWidget = null;
        faceResult = null;
        faceSpaceAvailable = false;
        geometryCache.clear();
    }

    public void update(
            Direction currentFace) {
        face = currentFace;
        if (faceResult != null) {
            faceResult.setFace(currentFace);
        }
        updateAvailability();
    }

    private void assembleScene(
            PreviewSceneState scene,
            SceneGeometryCache geometryCache,
            Runnable changed,
            Region region) {
        if (region.width() <= 0
                || region.height() <= 0) {
            sceneWidget = null;
            return;
        }
        host.addComponent(new PanelComponent(
                region.left(),
                region.top(),
                region.width(),
                region.height(),
                UilibWorkbenchTheme.SURFACE_PANEL));
        int canvasWidth = region.width()
                - PANEL_INSET * 2;
        int canvasHeight = region.height()
                - PANEL_INSET * 2
                - SCENE_CAPTION_HEIGHT;
        if (canvasWidth < MIN_SCENE_CANVAS_WIDTH
                || canvasHeight < MIN_SCENE_CANVAS_HEIGHT) {
            sceneWidget = null;
            return;
        }
        InteractiveBlockPreviewWidget widget =
                new InteractiveBlockPreviewWidget(
                        canvasWidth,
                        canvasHeight,
                        scene,
                        geometryCache,
                        renderPort,
                        changed);
        widget.setX(region.left() + PANEL_INSET);
        widget.setY(region.top() + PANEL_INSET);
        sceneWidget = widget;
        host.addWidget(widget);
        Component controls = Component.translatable(
                "gui.autoseamblend.preview.controls");
        Font font = Minecraft.getInstance().font;
        if (font.width(controls) <= canvasWidth) {
            host.addText(
                    controls,
                    region.left() + PANEL_INSET,
                    region.top()
                            + region.height()
                            - PANEL_INSET
                            - font.lineHeight,
                    UilibWorkbenchTheme.TEXT_SECONDARY);
        }
    }

    private void assembleFace(
            PreviewSceneState scene,
            SceneGeometryCache geometryCache,
            Region region) {
        if (region.width() <= 0
                || region.height() <= 0) {
            faceResult = null;
            faceSpaceAvailable = false;
            updateAvailability();
            return;
        }
        host.addComponent(new PanelComponent(
                region.left(),
                region.top(),
                region.width(),
                region.height(),
                UilibWorkbenchTheme.SURFACE_PANEL));
        Component title = Component.translatable(
                "gui.autoseamblend.preview.exact_face");
        Font font = Minecraft.getInstance().font;
        int textWidth = Math.max(
                0,
                region.width() - PANEL_INSET * 2);
        if (font.width(title) <= textWidth) {
            host.addText(
                    title,
                    region.left() + PANEL_INSET,
                    region.top() + PANEL_INSET,
                    UilibWorkbenchTheme.TEXT_PRIMARY);
        }
        int availableWidth = Math.max(
                0,
                region.width() - PANEL_INSET * 2);
        int availableHeight = Math.max(
                0,
                region.height()
                        - FACE_CONTENT_TOP
                        - PANEL_INSET);
        faceSpaceAvailable = RuntimeFaceResultComponent.fits(
                availableWidth,
                availableHeight,
                font.lineHeight);
        if (!faceSpaceAvailable) {
            faceResult = null;
            updateAvailability();
            if (font.width(availabilityText) <= textWidth) {
                availability.setX(
                        region.left() + PANEL_INSET);
                availability.setY(
                        region.top() + FACE_CONTENT_TOP);
                host.addComponent(availability);
            }
            return;
        }
        RuntimeFaceResultComponent face =
                new RuntimeFaceResultComponent(
                        availableWidth,
                        availableHeight,
                        scene,
                        geometryCache,
                        renderPort);
        face.setX(region.left() + PANEL_INSET);
        face.setY(region.top() + FACE_CONTENT_TOP);
        face.setFace(this.face);
        faceResult = face;
        host.addComponent(face);
        updateAvailability();
        if (font.width(availabilityText) <= textWidth) {
            availability.setX(
                    region.left() + PANEL_INSET);
            availability.setY(
                    region.top() + FACE_CONTENT_TOP);
            host.addComponent(availability);
        }
    }

    private void updateAvailability() {
        availabilityText =
                face == null || !faceSpaceAvailable
                        ? Component.translatable(
                                        "gui.autoseamblend.preview.unavailable")
                                .copy()
                                .withStyle(style -> style.withColor(TextColor.fromRgb(UilibWorkbenchTheme.STATUS_ERROR)))
                        : Component.empty();
        availability.setText(new Text(
                Minecraft.getInstance().font,
                availabilityText));
    }

    private record Region(
            int left,
            int top,
            int width,
            int height) {}

    /**
     * 中文：保持 8px 分区间距；正常窗口为单面投影保留最小可读区域，极小窗口则优先保证两区均不越界。
     *
     * English:
     * Preserves the 8px region gap and normally reserves a readable face
     * projection, while tiny windows prioritize keeping both regions in bounds.
     */
    private record Regions(
            Region scene,
            Region face) {
        private static Regions within(
                int left,
                int top,
                int width,
                int height) {
            int safeWidth = Math.max(0, width);
            int safeHeight = Math.max(0, height);
            if (safeWidth < STACK_THRESHOLD) {
                int minimumStackHeight =
                        MIN_SCENE_REGION_HEIGHT
                                + GAP
                                + MIN_FACE_REGION_HEIGHT;
                if (safeHeight < minimumStackHeight) {
                    return new Regions(
                            new Region(
                                    left,
                                    top,
                                    safeWidth,
                                    safeHeight),
                            new Region(
                                    left,
                                    top + safeHeight,
                                    safeWidth,
                                    0));
                }
                int usableHeight = safeHeight - GAP;
                int faceHeight = Math.max(
                        MIN_FACE_REGION_HEIGHT,
                        usableHeight / 2);
                faceHeight = Math.min(
                        faceHeight,
                        usableHeight - MIN_SCENE_REGION_HEIGHT);
                int sceneHeight = usableHeight - faceHeight;
                return new Regions(
                        new Region(
                                left,
                                top,
                                safeWidth,
                                sceneHeight),
                        new Region(
                                left,
                                top + sceneHeight + GAP,
                                safeWidth,
                                faceHeight));
            }
            int faceWidth = Math.min(
                    MAX_FACE_REGION_WIDTH,
                    Math.max(
                            MIN_FACE_REGION_WIDTH,
                            safeWidth * 2 / 5));
            int sceneWidth = Math.max(
                    0,
                    safeWidth - faceWidth - GAP);
            return new Regions(
                    new Region(
                            left,
                            top,
                            sceneWidth,
                            safeHeight),
                    new Region(
                            left + sceneWidth + GAP,
                            top,
                            faceWidth,
                            safeHeight));
        }
    }
}
