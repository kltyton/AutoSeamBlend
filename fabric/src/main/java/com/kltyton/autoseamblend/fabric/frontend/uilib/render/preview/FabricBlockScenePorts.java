package com.kltyton.autoseamblend.fabric.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.authoring.preview.BlockPreviewTint;
import com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition;
import com.kltyton.autoseamblend.authoring.preview.PreviewSceneQuadProcessing;
import com.kltyton.autoseamblend.authoring.preview.VirtualPreviewLevel;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricModelCapture;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext.QuadTransform;

/**
 * 中文：把 common 上下文模型捕获、发布读锁、动态 tint 与场景提交接到 Fabric 渲染路径。
 *
 * English: Adapts common contextual model capture, publication locking,
 * dynamic tint, and scene submission to the Fabric rendering path.
 */
public final class FabricBlockScenePorts {
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

    private FabricBlockScenePorts() {}

    public static SceneGeometryCache geometryCache() {
        return new SceneGeometryCache(
                PUBLICATION,
                FabricBlockScenePorts::capture);
    }

    public static void submit(
            GuiGraphics graphics,
            RenderRequest request) {
        Objects.requireNonNull(graphics, "graphics");
        FabricBlockScenePictureInPictureRenderer.render(
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
        // 中文：门每个节点只计算一次并同时决定模型选择与处理路径：Continuity 已注册预览
        // 处理器，保留 pre-wrapper 基础模型 + 公共处理器链；Athena/Fusion 未注册处理器，
        // 直接选择模型管理器中的已装饰运行时模型，由其自身发射连接纹理 Quad。
        // English: The gate is computed once per node and drives both model selection and
        // processing: Continuity registers a preview processor and keeps the pre-wrapper base
        // model plus the shared processor chain; Athena/Fusion register none and instead select
        // the decorated runtime model from the model manager, whose own emission produces the
        // connected-texture quads.
        boolean sceneProcessor =
                PreviewSceneQuadProcessing
                        .currentEngineHasSceneProcessor();
        BakedModel model = sceneProcessor
                ? previewModel(
                        minecraft,
                        state)
                : minecraft
                        .getModelManager()
                        .getModel(BlockModelShaper
                                .stateToModelLocation(state));
        return runtimeNode(
                minecraft,
                (FabricBakedModel) model,
                level,
                state,
                worldPosition,
                sceneOffset,
                sceneProcessor);
    }

    /**
     * 中文：优先使用发布代次的 pre-wrapper 基础模型（与 NeoForge getQuads 同源），
     * 缺失或非 FRAPI 模型时回退到模型管理器当前模型；原始 Quad 随后统一交给
     * 公共处理器链，避免依赖包装器是否已挂载。
     *
     * English: Prefers the published-generation pre-wrapper base model (same
     * source as NeoForge's getQuads) and falls back to the model-manager model
     * when it is missing or not a FRAPI model; raw quads are then
     * routed through the shared processor chain instead of relying on whether a
     * wrapper was mounted.
     */
    private static BakedModel previewModel(
            Minecraft minecraft,
            BlockState state) {
        BakedModel base = FabricModelCapture
                .latestBaseModels()
                .get(state);
        if (base instanceof FabricBakedModel) {
            return base;
        }
        return minecraft
                .getModelManager()
                .getModel(BlockModelShaper
                        .stateToModelLocation(state));
    }

    /**
     * 中文：从选定模型发射 Quad；已注册预览处理器的引擎（Continuity）再经公共处理器链
     * 应用真实 Continuity processor（含 VirtualPreviewLevel 世界/邻接上下文），其余引擎
     * 直接复用已装饰运行时包装器的发射结果。空结果保留 raw quads，不冒充成功。
     *
     * English: Emits quads from the selected model; engines with a registered preview
     * processor (Continuity) then route them through the shared processor chain with the
     * VirtualPreviewLevel world/adjacency context, while the remaining engines reuse the
     * decorated runtime wrapper's emission directly. An empty result keeps the raw quads
     * instead of pretending success.
     */
    private static Node runtimeNode(
            Minecraft minecraft,
            FabricBakedModel model,
            BlockAndTintGetter level,
            BlockState state,
            BlockPos worldPosition,
            BlockPos sceneOffset,
            boolean sceneProcessor) {
        SpriteFinder sprites = SpriteFinder.get(
                minecraft.getModelManager()
                        .getAtlas(
                                TextureAtlas.LOCATION_BLOCKS));
        ArrayList<BakedQuad> quads = new ArrayList<>();
        boolean[] translucent = {false};
        CapturingEmitter emitter = new CapturingEmitter(
                sprites,
                quads,
                translucent,
                level,
                worldPosition);
        model.emitBlockQuads(
                level,
                state,
                worldPosition,
                () -> RandomSource.create(
                        worldPosition.asLong()),
                emitter.asRenderContext());
        // 中文：已注册处理器（Continuity）时走公共处理器链；否则直接提交已装饰包装器
        // 发射的 Quad，避免二次处理。
        // English: With a registered processor (Continuity) the shared processor chain runs;
        // otherwise the quads emitted by the decorated wrapper are submitted directly, avoiding
        // any second processing pass.
        List<BakedQuad> submitQuads = sceneProcessor
                ? PreviewSceneQuadProcessing.process(
                        level,
                        state,
                        worldPosition,
                        quads)
                : quads;
        return new Node(
                submitQuads,
                TINT.values(
                        level,
                        state,
                        worldPosition),
                translucent[0],
                sceneOffset.getX(),
                sceneOffset.getY(),
                sceneOffset.getZ());
    }

    /**
     * 中文：包装 FRAPI QuadEmitter，在 emit 时按 UV 解析精确精灵并归档 BakedQuad。
     *
     * English: Wraps a FRAPI QuadEmitter and, on emit, resolves the exact
     * sprite by UV and archives the resulting BakedQuad.
     */
    private static final class CapturingEmitter
            implements QuadEmitter {
        private final QuadEmitter delegate =
                RendererAccess.INSTANCE
                        .getRenderer()
                        .meshBuilder()
                        .getEmitter();
        private final SpriteFinder sprites;
        private final List<BakedQuad> output;
        private final boolean[] translucent;
        private final BlockAndTintGetter level;
        private final BlockPos worldPosition;

        private CapturingEmitter(
                SpriteFinder sprites,
                List<BakedQuad> output,
                boolean[] translucent,
                BlockAndTintGetter level,
                BlockPos worldPosition) {
            this.sprites = sprites;
            this.output = output;
            this.translucent = translucent;
            this.level = level;
            this.worldPosition = worldPosition;
        }

        private RenderContext asRenderContext() {
            return new CapturingContext(
                    this,
                    level,
                    worldPosition);
        }

        @Override
        public QuadEmitter emit() {
            TextureAtlasSprite sprite =
                    sprites.find(delegate);
            output.add(delegate.toBakedQuad(sprite));
            if (delegate.material().blendMode()
                    == BlendMode.TRANSLUCENT) {
                translucent[0] = true;
            }
            return delegate.emit();
        }

        @Override
        public QuadEmitter pos(
                int vertexIndex,
                float x,
                float y,
                float z) {
            return delegate.pos(
                    vertexIndex,
                    x,
                    y,
                    z);
        }

        @Override
        public QuadEmitter color(
                int vertexIndex,
                int color) {
            return delegate.color(
                    vertexIndex,
                    color);
        }

        @Override
        public QuadEmitter uv(
                int vertexIndex,
                float u,
                float v) {
            return delegate.uv(
                    vertexIndex,
                    u,
                    v);
        }

        @Override
        public QuadEmitter spriteBake(
                TextureAtlasSprite sprite,
                int vertexIndex) {
            return delegate.spriteBake(
                    sprite,
                    vertexIndex);
        }

        @Override
        public QuadEmitter lightmap(
                int vertexIndex,
                int lightmap) {
            return delegate.lightmap(
                    vertexIndex,
                    lightmap);
        }

        @Override
        public QuadEmitter normal(
                int vertexIndex,
                float x,
                float y,
                float z) {
            return delegate.normal(
                    vertexIndex,
                    x,
                    y,
                    z);
        }

        @Override
        public QuadEmitter cullFace(
                Direction face) {
            return delegate.cullFace(face);
        }

        @Override
        public QuadEmitter nominalFace(
                Direction face) {
            return delegate.nominalFace(face);
        }

        @Override
        public QuadEmitter material(
                net.fabricmc.fabric.api.renderer.v1.material
                        .RenderMaterial material) {
            return delegate.material(material);
        }

        @Override
        public QuadEmitter colorIndex(
                int colorIndex) {
            return delegate.colorIndex(colorIndex);
        }

        @Override
        public QuadEmitter tag(int tag) {
            return delegate.tag(tag);
        }

        @Override
        public QuadEmitter copyFrom(
                net.fabricmc.fabric.api.renderer.v1.mesh
                        .QuadView quad) {
            return delegate.copyFrom(quad);
        }

        @Override
        public QuadEmitter fromVanilla(
                int[] quadData,
                int startIndex) {
            return delegate.fromVanilla(
                    quadData,
                    startIndex);
        }

        @Override
        public QuadEmitter fromVanilla(
                BakedQuad quad,
                net.fabricmc.fabric.api.renderer.v1.material
                        .RenderMaterial material,
                Direction face) {
            return delegate.fromVanilla(
                    quad,
                    material,
                    face);
        }

        @Override
        public float x(int vertexIndex) {
            return delegate.x(vertexIndex);
        }

        @Override
        public float y(int vertexIndex) {
            return delegate.y(vertexIndex);
        }

        @Override
        public float z(int vertexIndex) {
            return delegate.z(vertexIndex);
        }

        @Override
        public float posByIndex(
                int vertexIndex,
                int coordinateIndex) {
            return delegate.posByIndex(
                    vertexIndex,
                    coordinateIndex);
        }

        @Override
        public org.joml.Vector3f copyPos(
                int vertexIndex,
                org.joml.Vector3f target) {
            return delegate.copyPos(
                    vertexIndex,
                    target);
        }

        @Override
        public int color(int vertexIndex) {
            return delegate.color(vertexIndex);
        }

        @Override
        public float u(int vertexIndex) {
            return delegate.u(vertexIndex);
        }

        @Override
        public float v(int vertexIndex) {
            return delegate.v(vertexIndex);
        }

        @Override
        public org.joml.Vector2f copyUv(
                int vertexIndex,
                org.joml.Vector2f target) {
            return delegate.copyUv(
                    vertexIndex,
                    target);
        }

        @Override
        public int lightmap(int vertexIndex) {
            return delegate.lightmap(vertexIndex);
        }

        @Override
        public boolean hasNormal(
                int vertexIndex) {
            return delegate.hasNormal(vertexIndex);
        }

        @Override
        public float normalX(
                int vertexIndex) {
            return delegate.normalX(vertexIndex);
        }

        @Override
        public float normalY(
                int vertexIndex) {
            return delegate.normalY(vertexIndex);
        }

        @Override
        public float normalZ(
                int vertexIndex) {
            return delegate.normalZ(vertexIndex);
        }

        @Override
        public org.joml.Vector3f copyNormal(
                int vertexIndex,
                org.joml.Vector3f target) {
            return delegate.copyNormal(
                    vertexIndex,
                    target);
        }

        @Override
        public Direction cullFace() {
            return delegate.cullFace();
        }

        @Override
        public Direction lightFace() {
            return delegate.lightFace();
        }

        @Override
        public Direction nominalFace() {
            return delegate.nominalFace();
        }

        @Override
        public org.joml.Vector3f faceNormal() {
            return delegate.faceNormal();
        }

        @Override
        public net.fabricmc.fabric.api.renderer.v1.material
                .RenderMaterial material() {
            return delegate.material();
        }

        @Override
        public int colorIndex() {
            return delegate.colorIndex();
        }

        @Override
        public int tag() {
            return delegate.tag();
        }

        @Override
        public void toVanilla(
                int[] quadData,
                int startIndex) {
            delegate.toVanilla(
                    quadData,
                    startIndex);
        }
    }

    private static final class CapturingContext
            implements RenderContext {
        private final CapturingEmitter emitter;
        private final BlockAndTintGetter level;
        private final BlockPos worldPosition;

        private CapturingContext(
                CapturingEmitter emitter,
                BlockAndTintGetter level,
                BlockPos worldPosition) {
            this.emitter = emitter;
            this.level = level;
            this.worldPosition = worldPosition;
        }

        @Override
        public QuadEmitter getEmitter() {
            return emitter;
        }

        @Override
        public void pushTransform(
                QuadTransform transform) {}

        @Override
        public void popTransform() {}

        @Override
        @SuppressWarnings({"removal", "deprecation"})
        public RenderContext.BakedModelConsumer bakedModelConsumer() {
            // 中文：FRAPI 1.21.1 把 bakedModelConsumer()/BakedModelConsumer 标为
            // for-removal，推荐直接对目标模型调用 FabricBakedModel.emitBlockQuads；
            // 抽象旧方法仍需实现，故保留桥接：FRAPI 嵌套模型改走 emitBlockQuads
            // （同一捕获 emitter，语义不变），null state/非 FRAPI 模型保持原
            // getQuads 回退，额外 Quad 仍经同一 emitter 捕获。
            // English: FRAPI 1.21.1 marks bakedModelConsumer()/BakedModelConsumer
            // for removal and recommends calling FabricBakedModel.emitBlockQuads
            // directly; the abstract legacy method still must be implemented, so
            // this bridge keeps FRAPI nested models on emitBlockQuads (same
            // capturing emitter, unchanged semantics) while null-state/non-FRAPI
            // models keep the vanilla getQuads fallback; extra quads still flow
            // through the same emitter.
            return new RenderContext.BakedModelConsumer() {
                @Override
                public void accept(
                        BakedModel model) {
                    accept(model, null);
                }

                @Override
                public void accept(
                        BakedModel model,
                        BlockState state) {
                    Supplier<RandomSource> random =
                            () -> RandomSource.create(
                                    System.nanoTime());
                    if (state != null
                            && model
                                    instanceof FabricBakedModel
                                            fabric) {
                        fabric.emitBlockQuads(
                                level,
                                state,
                                worldPosition,
                                random,
                                CapturingContext.this);
                        return;
                    }
                    for (Direction direction
                            : Direction.values()) {
                        emitVanilla(
                                model.getQuads(
                                        state,
                                        direction,
                                        random.get()));
                    }
                    emitVanilla(
                            model.getQuads(
                                    state,
                                    null,
                                    random.get()));
                }

                private void emitVanilla(
                        List<BakedQuad> quads) {
                    for (BakedQuad quad : quads) {
                        emitter.fromVanilla(
                                quad,
                                RendererAccess.INSTANCE
                                        .getRenderer()
                                        .materialFinder()
                                        .find(),
                                quad.getDirection());
                        emitter.emit();
                    }
                }
            };
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
