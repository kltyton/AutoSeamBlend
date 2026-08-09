package com.kltyton.autoseamblend.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneState;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：持有左右预览共用的不可变模型几何，并把 Loader 捕获、发布、动态 tint 与提交隔离为显式端口。
 * English: Owns immutable model geometry shared by both preview panes while
 * isolating Loader capture, publication, dynamic tint, and submission behind
 * explicit ports.
 */
public final class BlockSceneGeometry {
    private BlockSceneGeometry() {}

    /** 中文：在一个 Loader 发布读区间内执行完整捕获。 / English: Runs one complete capture inside a Loader publication read interval. */
    public interface PublicationPort {
        SceneGeometry capture(PublishedCapture capture);

        long currentGeneration();
    }

    /** 中文：接收已锁定的表面代次。 / English: Receives the surface generation pinned by publication. */
    @FunctionalInterface
    public interface PublishedCapture {
        SceneGeometry capture(long surfaceGeneration);
    }

    /** 中文：由 Loader 捕获上下文模型部件。 / English: Captures contextual model parts through the Loader adapter. */
    @FunctionalInterface
    public interface SceneCapturePort {
        SceneGeometry capture(PreviewSceneState scene);
    }

    /** 中文：由 Loader 解析方块全部 tint 层。 / English: Resolves all block tint layers through the Loader adapter. */
    @FunctionalInterface
    public interface TintPort {
        int[] values(
                BlockAndTintGetter level,
                BlockState state,
                BlockPos position);
    }

    /** 中文：由 Loader 把 common 请求提交到已注册的 PIP 渲染器。 / English: Lets the Loader submit a common request to its registered PIP renderer. */
    @FunctionalInterface
    public interface RenderPort {
        void submit(
                GuiGraphics graphics,
                RenderRequest request);
    }

    public record Node(
            List<BakedQuad> quads,
            int[] tintLayers,
            boolean translucent,
            int x,
            int y,
            int z) {
        public Node {
            quads = List.copyOf(Objects.requireNonNull(quads, "quads"));
            tintLayers = Objects.requireNonNull(
                    tintLayers,
                    "tintLayers").clone();
        }
    }

    /** 中文：可跨帧复用的昂贵几何快照。 / English: Expensive geometry snapshot reusable across frames. */
    public record SceneGeometry(
            List<Node> nodes,
            BlockPos origin,
            long sceneRevision,
            long surfaceGeneration) {
        public SceneGeometry {
            nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
            Objects.requireNonNull(origin, "origin");
        }

        public boolean matches(
                PreviewSceneState scene,
                long currentGeneration,
                BlockPos currentOrigin) {
            return sceneRevision == scene.sceneRevision()
                    && surfaceGeneration == currentGeneration
                    && origin.equals(currentOrigin);
        }

        public RenderRequest renderRequest(
                PreviewSceneState scene,
                int x0,
                int y0,
                int x1,
                int y1) {
            return new RenderRequest(
                    nodes,
                    scene.yaw(),
                    scene.pitch(),
                    scene.panX(),
                    scene.panY(),
                    x0,
                    y0,
                    x1,
                    y1,
                    38.0F * scene.zoom(),
                    false);
        }
    }

    /** 中文：Loader 无关的 PIP 场景提交值。 / English: Loader-independent PIP scene submission value. */
    public record RenderRequest(
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
            boolean flatLighting) {
        public RenderRequest {
            nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
            if (nodes.isEmpty()) {
                throw new IllegalArgumentException("block scene needs a target node");
            }
            if (x1 <= x0 || y1 <= y0 || scale <= 0.0F) {
                throw new IllegalArgumentException("invalid block scene viewport");
            }
        }
    }

    /**
     * 中文：左右预览共享的场景缓存；场景修订、资源代次或玩家锚点任一变化都会失效。
     * English: Cache shared by both panes; any scene revision, publication
     * generation, or player-origin change invalidates it.
     */
    public static final class SceneGeometryCache {
        private final PublicationPort publication;
        private final SceneCapturePort capture;
        private PreviewSceneState owner;
        private SceneGeometry geometry;

        public SceneGeometryCache(
                PublicationPort publication,
                SceneCapturePort capture) {
            this.publication = Objects.requireNonNull(
                    publication,
                    "publication");
            this.capture = Objects.requireNonNull(capture, "capture");
        }

        public SceneGeometry geometry(PreviewSceneState scene) {
            PreviewSceneState checked = Objects.requireNonNull(scene, "scene");
            BlockPos origin = currentOrigin();
            if (owner != checked
                    || geometry == null
                    || !geometry.matches(
                            checked,
                            publication.currentGeneration(),
                            origin)) {
                owner = checked;
                geometry = capture.capture(checked);
            }
            return geometry;
        }

        public void clear() {
            owner = null;
            geometry = null;
        }

        private static BlockPos currentOrigin() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player == null
                    ? BlockPos.ZERO
                    : minecraft.player.blockPosition();
        }
    }
}
