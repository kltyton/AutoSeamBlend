package com.kltyton.autoseamblend.forge.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.authoring.preview.BlockPreviewTint;
import com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition;
import com.kltyton.autoseamblend.authoring.preview.PreviewSceneQuadProcessing;
import com.kltyton.autoseamblend.authoring.preview.VirtualPreviewLevel;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneMath;
import com.kltyton.autoseamblend.frontend.model.preview.PreviewSceneState;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.Node;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.PublicationPort;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.PublishedCapture;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.RenderRequest;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometry;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.SceneGeometryCache;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneGeometry.TintPort;
import com.kltyton.autoseamblend.frontend.uilib.render.preview.BlockSceneRenderState;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * 中文：把 common 上下文模型捕获、发布读锁、动态 tint 与场景提交接到 Forge 渲染路径。
 *
 * English: Adapts common contextual model capture, publication locking,
 * dynamic tint, and scene submission to the Forge rendering path.
 */
public final class ForgeBlockScenePorts {
    private static final PublicationPort PUBLICATION =
            new PublicationPort() {
                @Override
                public SceneGeometry capture(
                        PublishedCapture capture) {
                    return ReloadPublication.read(runtime ->
                            capture.capture(
                                    runtime.surfaces()
                                            .generation()));
                }

                @Override
                public long currentGeneration() {
                    return ReloadPublication.current()
                            .surfaces()
                            .generation();
                }
            };
    private static final TintPort TINT =
            BlockPreviewTint::values;
    private static final BlockAndTintGetter EMPTY =
            EmptyLevel.INSTANCE;

    private ForgeBlockScenePorts() {}

    public static SceneGeometryCache geometryCache() {
        return new SceneGeometryCache(
                PUBLICATION,
                ForgeBlockScenePorts::capture);
    }

    public static void submit(
            GuiGraphics graphics,
            RenderRequest request) {
        Objects.requireNonNull(graphics, "graphics");
        BlockScenePictureInPictureRenderer.render(
                graphics,
                new BlockSceneRenderState(
                        request,
                        new ScreenRectangle(
                                request.x0(),
                                request.y0(),
                                request.x1()
                                        - request.x0(),
                                request.y1()
                                        - request.y0())));
    }

    private static SceneGeometry capture(
            PreviewSceneState scene) {
        Objects.requireNonNull(scene, "scene");
        return PUBLICATION.capture(
                surfaceGeneration ->
                        capturePublished(
                                scene,
                                surfaceGeneration));
    }

    private static SceneGeometry capturePublished(
            PreviewSceneState scene,
            long surfaceGeneration) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos origin = minecraft.player == null
                ? BlockPos.ZERO
                : minecraft.player.blockPosition();
        BlockAndTintGetter delegate = minecraft.level == null
                ? EMPTY
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
        BakedModel model = minecraft
                .getModelManager()
                .getModel(BlockModelShaper
                        .stateToModelLocation(state));
        // 中文：门每个节点只计算一次并同时决定取数路径与处理路径：Continuity 已注册预览
        // 处理器，保留三参原始 getQuads + 公共处理器链；CTM/Athena/Fusion 未注册处理器，
        // 直接复用已装饰运行时包装器，避免"未注册处理器"警告与二次处理。
        // English: The gate is computed once per node and drives both quad collection and
        // processing: Continuity registers a preview processor and keeps the three-argument
        // raw getQuads plus the shared processor chain; CTM/Athena/Fusion register none and
        // reuse the decorated runtime wrapper directly, avoiding the missing-processor warning
        // and any second processing pass.
        boolean sceneProcessor =
                PreviewSceneQuadProcessing
                        .currentEngineHasSceneProcessor();
        RandomSource random = RandomSource.create(
                worldPosition.asLong());
        ArrayList<BakedQuad> quads = new ArrayList<>();
        if (sceneProcessor) {
            for (Direction direction : Direction.values()) {
                quads.addAll(model.getQuads(
                        state,
                        direction,
                        random));
            }
            quads.addAll(model.getQuads(
                    state,
                    null,
                    random));
        } else {
            // 中文：Forge 21.1 包装器经 getModelData 携带 (level,pos) 上下文，五参
            // getQuads 只在 context 存在时执行连接纹理。必须按模型自身广告的 render
            // types 收集所有 pass（玻璃 translucent、石 solid、可 overlay 时额外 cutout），
            // 保留 direction 与 null 桶，并跨 pass 按 BakedQuad 对象身份去重；这与
            // 26.1.2 collectParts 无层过滤收集全部 parts 的语义等价。每次调用都从固定
            // seed 新建 RandomSource，避免复用已消耗实例导致后续桶不确定。
            // English: Forge 21.1 wrappers carry the (level,pos) context through
            // getModelData, and the five-argument getQuads only blends when that context
            // exists. All advertised render types must be collected (translucent glass,
            // solid stone, plus the extra cutout pass when an overlay is possible), keeping
            // both direction and null buckets, deduplicated by BakedQuad object identity
            // across passes; this is the 1.20.1 equivalent of 26.1.2's layer-unfiltered
            // collectParts. A fresh RandomSource is created from the fixed seed for every
            // call so a consumed instance never makes later buckets non-deterministic.
            ModelData modelData = model.getModelData(
                    level,
                    worldPosition,
                    state,
                    ModelData.EMPTY);
            quads.addAll(collectSceneQuads(
                    model,
                    state,
                    worldPosition.asLong(),
                    modelData));
        }
        List<BakedQuad> submitQuads = sceneProcessor
                ? PreviewSceneQuadProcessing.process(
                        level,
                        state,
                        worldPosition,
                        quads)
                : quads;
        boolean translucent = model.getRenderTypes(
                        state,
                        RandomSource.create(
                                worldPosition.asLong()),
                        ModelData.EMPTY)
                .contains(RenderType.translucent());
        return new Node(
                submitQuads,
                TINT.values(
                        level,
                        state,
                        worldPosition),
                translucent,
                sceneOffset.getX(),
                sceneOffset.getY(),
                sceneOffset.getZ());
    }

    /**
     * 中文：按模型广告的所有 render pass 收集场景 quad（direction 与 null 桶），跨 pass
     * 按 BakedQuad 对象身份去重。每次 getRenderTypes/getQuads 都从固定 seed 新建
     * RandomSource，保证确定性且不消耗调用方随机源。
     *
     * <p>English: Collects scene quads across every render pass advertised by the model
     * (direction and null buckets), deduplicated by BakedQuad object identity across passes.
     * A fresh RandomSource is created from the fixed seed for every getRenderTypes/getQuads
     * call, keeping the result deterministic without consuming the caller's random source.
     */
    static List<BakedQuad> collectSceneQuads(
            BakedModel model,
            BlockState state,
            long seed,
            ModelData modelData) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(modelData, "modelData");
        IdentityHashMap<BakedQuad, Boolean> seen =
                new IdentityHashMap<>();
        ArrayList<BakedQuad> quads = new ArrayList<>();
        for (RenderType type : model.getRenderTypes(
                state,
                RandomSource.create(seed),
                modelData)) {
            for (Direction direction : Direction.values()) {
                addDedup(
                        quads,
                        seen,
                        model.getQuads(
                                state,
                                direction,
                                RandomSource.create(seed),
                                modelData,
                                type));
            }
            addDedup(
                    quads,
                    seen,
                    model.getQuads(
                            state,
                            null,
                            RandomSource.create(seed),
                            modelData,
                            type));
        }
        return List.copyOf(quads);
    }

    private static void addDedup(
            ArrayList<BakedQuad> output,
            IdentityHashMap<BakedQuad, Boolean> seen,
            List<BakedQuad> source) {
        for (BakedQuad quad : source) {
            if (seen.put(quad, Boolean.TRUE) == null) {
                output.add(quad);
            }
        }
    }

    /**
     * 中文：minecraft.level 为空时的只读空世界切片。
     *
     * English: Read-only empty world slice used when the client level is null.
     */
    private enum EmptyLevel
            implements BlockAndTintGetter {
        INSTANCE;

        @Override
        public BlockState getBlockState(
                BlockPos pos) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(
                BlockPos pos) {
            return Blocks.AIR.defaultBlockState()
                    .getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(
                BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 0;
        }

        @Override
        public int getMinBuildHeight() {
            return 0;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return null;
        }

        @Override
        public float getShade(
                Direction direction,
                boolean shade) {
            return 1.0F;
        }

        @Override
        public int getBlockTint(
                BlockPos pos,
                ColorResolver resolver) {
            return -1;
        }
    }
}
