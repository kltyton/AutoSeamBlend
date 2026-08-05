package com.kltyton.autoseamblend.neoforge.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition;
import com.kltyton.autoseamblend.authoring.preview.VirtualPreviewLevel;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneState;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.Node;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.PublicationPort;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.PublishedCapture;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.RenderRequest;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometry;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometryCache;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.TintPort;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneRenderState;
import com.kltyton.autoseamblend.authoring.preview.BlockPreviewTint;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：把 NeoForge 上下文模型捕获、发布读锁、动态 tint 与 PIP 提交接到 common 端口。
 * English: Adapts NeoForge contextual model capture, publication locking,
 * dynamic tint, and PIP submission to the common ports.
 */
public final class NeoForgeBlockScenePorts {
    private static final PublicationPort PUBLICATION = new PublicationPort() {
        @Override
        public SceneGeometry capture(PublishedCapture capture) {
            return ReloadPublication.read(runtime ->
                    capture.capture(runtime.surfaces().generation()));
        }

        @Override
        public long currentGeneration() {
            return ReloadPublication.current()
                    .surfaces()
                    .generation();
        }
    };
    private static final TintPort TINT = BlockPreviewTint::values;

    private NeoForgeBlockScenePorts() {}

    public static SceneGeometryCache geometryCache() {
        return new SceneGeometryCache(
                PUBLICATION,
                NeoForgeBlockScenePorts::capture);
    }

    public static void submit(
            GuiGraphicsExtractor graphics,
            RenderRequest request) {
        Objects.requireNonNull(graphics, "graphics")
                .submitPictureInPictureRenderState(
                        new BlockSceneRenderState(
                                request,
                                graphics.peekScissorStack()));
    }

    private static SceneGeometry capture(PreviewSceneState scene) {
        Objects.requireNonNull(scene, "scene");
        return PUBLICATION.capture(surfaceGeneration ->
                capturePublished(scene, surfaceGeneration));
    }

    /**
     * 中文：整个场景在同一发布读锁内捕获，模型包装器不会观察混合资源代次。
     * English: Captures the whole scene under one publication read lock so
     * model wrappers cannot observe mixed resource generations.
     */
    private static SceneGeometry capturePublished(
            PreviewSceneState scene,
            long surfaceGeneration) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos origin = minecraft.player == null
                ? BlockPos.ZERO
                : minecraft.player.blockPosition();
        BlockAndTintGetter delegate = minecraft.level == null
                ? BlockAndTintGetter.EMPTY
                : minecraft.level;
        BlockAndTintGetter virtual = new VirtualPreviewLevel(
                delegate,
                origin,
                scene.centerState(),
                scene.neighbors());
        ArrayList<Node> nodes = new ArrayList<>();
        nodes.add(node(
                minecraft,
                virtual,
                scene.centerState(),
                origin,
                BlockPos.ZERO));
        for (Map.Entry<PreviewNeighborPosition, BlockState> neighbor
                : scene.neighbors().entrySet()) {
            BlockPos offset = new BlockPos(
                    neighbor.getKey().x(),
                    neighbor.getKey().y(),
                    neighbor.getKey().z());
            nodes.add(node(
                    minecraft,
                    virtual,
                    neighbor.getValue(),
                    origin.offset(offset),
                    offset));
        }
        return new SceneGeometry(
                nodes,
                origin,
                scene.sceneRevision(),
                surfaceGeneration);
    }

    private static Node node(
            Minecraft minecraft,
            BlockAndTintGetter level,
            BlockState state,
            BlockPos worldPosition,
            BlockPos sceneOffset) {
        BlockStateModel model = minecraft
                .getModelManager()
                .getBlockStateModelSet()
                .get(state);
        ArrayList<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(
                level,
                worldPosition,
                state,
                RandomSource.create(worldPosition.asLong()),
                parts);
        return new Node(
                parts,
                TINT.values(level, state, worldPosition),
                model.hasMaterialFlag(level, worldPosition, state, 1),
                sceneOffset.getX(),
                sceneOffset.getY(),
                sceneOffset.getZ());
    }
}
