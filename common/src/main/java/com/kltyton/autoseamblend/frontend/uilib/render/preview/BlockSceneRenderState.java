package com.kltyton.autoseamblend.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.Node;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.RenderRequest;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/**
 * 中文：NeoForge PIP 注册所需的薄渲染状态；场景几何和值语义位于 common。
 * English: Thin render state required by NeoForge PIP registration; scene
 * geometry and value semantics live in common.
 */
public record BlockSceneRenderState(
        List<Node> nodes,
        float yaw,
        float pitch,
        float panX,
        float panY,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        boolean flatLighting,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds)
        implements PictureInPictureRenderState {
    public BlockSceneRenderState {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("block scene needs a target node");
        }
        if (x1 <= x0 || y1 <= y0 || scale <= 0.0F) {
            throw new IllegalArgumentException("invalid block scene viewport");
        }
        Objects.requireNonNull(bounds, "bounds");
    }

    public BlockSceneRenderState(
            RenderRequest request,
            ScreenRectangle scissorArea) {
        this(
                request.nodes(),
                request.yaw(),
                request.pitch(),
                request.panX(),
                request.panY(),
                request.x0(),
                request.y0(),
                request.x1(),
                request.y1(),
                request.scale(),
                request.flatLighting(),
                scissorArea,
                PictureInPictureRenderState.getBounds(
                        request.x0(),
                        request.y0(),
                        request.x1(),
                        request.y1(),
                        scissorArea));
    }
}
