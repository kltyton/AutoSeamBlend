package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionNativeQuadProcessor;
import com.kltyton.autoseamblend.compat.fusion.runtime.texture.FusionGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.fabric.runtime.texture.FabricBlockAtlasSpriteFinder;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftTopSurfaceResolver;
import com.kltyton.autoseamblend.runtime.render.DonorTintResolver;
import com.kltyton.autoseamblend.runtime.geometry.IdentityPreservingListBuilder;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionSheetMethodPlan;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：由 Fusion 支持的动态模型；Fusion 决定纹理方向和八邻域状态，AutoBlend 只选择已解析
 * 方法并把不可变补丁计划应用到 Fusion 可变 Quad。
 *
 * English: Fusion-backed dynamic model. Fusion determines texture orientation
 * and eight-neighbor state; AutoBlend only selects the resolved method and
 * applies the immutable patch plan to Fusion mutable quads.
 */
public final class FabricFusionConnectedBlockStateModel
        extends WrapperBlockStateModel {
    private final BlockState bakedState;
    private final ConcurrentMap<
                    ProcessorKey,
                    Optional<FusionNativeQuadProcessor>>
            processors = new ConcurrentHashMap<>();

    public FabricFusionConnectedBlockStateModel(
            BlockStateModel delegate,
            BlockState bakedState) {
        super(Objects.requireNonNull(
                delegate,
                "delegate"));
        this.bakedState = Objects.requireNonNull(
                bakedState,
                "bakedState");
    }

    @Override
    public void collectParts(
            RandomSource random,
            List<BlockStateModelPart> output) {
        super.collectParts(random, output);
    }

    @Override
    public void emitQuads(
            QuadEmitter emitter,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            RandomSource random,
            Predicate<Direction> cullTest) {
        if (state != bakedState) {
            super.emitQuads(
                    emitter,
                    level,
                    pos,
                    state,
                    random,
                    cullTest);
            return;
        }
        ReloadPublication.read(generation -> {
            emitQuads(
                    generation,
                    emitter,
                    level,
                    pos,
                    state,
                    random,
                    cullTest);
            return null;
        });
    }

    private void emitQuads(
            ReloadPublication.Generation generation,
            QuadEmitter emitter,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            RandomSource random,
            Predicate<Direction> cullTest) {
        long seed = random.nextLong();
        SpriteFinder spriteFinder =
                FabricBlockAtlasSpriteFinder.current();
        BufferingEmitter buffer =
                new BufferingEmitter(spriteFinder);
        super.emitQuads(
                buffer,
                level,
                pos,
                state,
                RandomSource.create(seed),
                cullTest);
        RuleRuntime.Snapshot rules =
                generation.selectors();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                generation.surfaces();
        for (BufferedQuad buffered : buffer.quads()) {
            BakedQuad quad = buffered.quad();
            TextureAtlasSprite sprite =
                    quad.materialInfo().sprite();
            Optional<EngineQuerySelection> selection =
                    EngineQueryRouter.select(
                            generation,
                            state,
                            level,
                            pos,
                            quad,
                            sprite);
            if (selection.isEmpty()
                    || selection.orElseThrow().family()
                            != EngineFamily.FUSION
                    || !selection.orElseThrow()
                            .runsAutoBlend()) {
                prepareEmission(
                        emitter,
                        buffered,
                        quad).emit();
                continue;
            }
            Optional<FaceSurface> face =
                    surfaces.face(
                            state,
                            quad.direction(),
                            sprite);
            if (face.isEmpty()) {
                prepareEmission(
                        emitter,
                        buffered,
                        quad).emit();
                continue;
            }
            FaceSurface surface = face.orElseThrow();
            FaceSurface inferenceSurface =
                    surfaces.preferredFace(
                                    state,
                                    surface.direction())
                            .orElse(surface);
            ConnectionMethod method = resolveMethod(
                    selection.orElseThrow(),
                    state,
                    inferenceSurface);
            if (method == ConnectionMethod.TOP) {
                Optional<TextureAtlasSprite> topSprite =
                        MinecraftTopSurfaceResolver
                                .resolve(
                                        level,
                                        pos,
                                        state,
                                        quad.direction(),
                                        rules.rules(),
                                        surfaces);
                BakedQuad top = topSprite
                                .map(topTexture ->
                                        FusionNativeQuadProcessor
                                                .retexture(
                                                        quad,
                                                        topTexture))
                                .orElse(quad);
                prepareEmission(
                        emitter,
                        buffered,
                        top).emit();
                continue;
            }
            if (FusionSheetMethodPlan.isReplacement(method)) {
                Optional<FusionNativeQuadProcessor> processor =
                        processor(
                                quad,
                                surface.sprite(),
                                state.getBlock(),
                                method,
                                surface.overlayProfile(),
                                Optional.empty(),
                                generation,
                                rules);
                if (processor.isEmpty()) {
                    prepareEmission(
                            emitter,
                            buffered,
                            quad).emit();
                    continue;
                }
                List<BakedQuad> replacements =
                        processor.orElseThrow()
                                .process(
                                        level,
                                        pos,
                                        state,
                                        seed);
                if (replacements.isEmpty()) {
                    prepareEmission(
                            emitter,
                            buffered,
                            quad).emit();
                } else {
                    for (BakedQuad replacement
                            : replacements) {
                        prepareEmission(
                                emitter,
                                buffered,
                                replacement).emit();
                    }
                }
                continue;
            }
            prepareEmission(
                    emitter,
                    buffered,
                    quad).emit();
            if (!method.overlayCapable()
                    || !surface.fullFace()
                    || !surface.facts()
                            .alphaOpaque()
                            .isTrue()) {
                continue;
            }
            List<Donor> donors = selectDonors(
                    level,
                    pos,
                    quad.direction(),
                    state,
                    rules.rules(),
                    surfaces);
            for (Donor donor : donors) {
                int tint = DonorTintResolver.resolve(
                        donor.state(),
                        level,
                        pos,
                        donor.surface().tintIndex());
                Optional<FusionNativeQuadProcessor> overlay =
                        processor(
                                quad,
                                donor.surface().sprite(),
                                donor.state().getBlock(),
                                donor.method(),
                                donor.surface()
                                        .overlayProfile(),
                                Optional.of(tint),
                                generation,
                                rules);
                if (overlay.isEmpty()) {
                    continue;
                }
                List<BakedQuad> overlayReplacements =
                        overlay.orElseThrow()
                                .process(
                                        level,
                                        pos,
                                donor.state(),
                                        seed);
                if (overlayReplacements.isEmpty()) {
                    continue;
                }
                for (BakedQuad replacement
                        : overlayReplacements) {
                    emitOverlayQuad(
                            emitter,
                            buffered,
                            replacement,
                            tint);
                }
            }
        }
    }

    /**
     * 中文：最终发射前的统一准备：fromBakedQuad 后恢复源 quad 的 cull/nominal/tag。
     * 26.1.2 BakedQuad 不保留 cullFace，PaneCapCullTransform 写入的端盖剔除桶必须
     * 在这里重新挂回；passthrough/top/native/none/overlay/empty 全部发射分支共用。
     *
     * English: Shared final-emission preparation: after fromBakedQuad, restore the
     * source quad's cull/nominal/tag. The 26.1.2 BakedQuad does not retain
     * cullFace, so the pane cap cull bucket written by PaneCapCullTransform must
     * be re-applied here; passthrough/top/native/none/overlay/empty all share it.
     */
    private static QuadEmitter prepareEmission(
            QuadEmitter emitter,
            BufferedQuad source,
            BakedQuad output) {
        QuadEmitter prepared =
                emitter.fromBakedQuad(output);
        prepared.cullFace(source.cullFace());
        prepared.nominalFace(source.nominalFace());
        prepared.tag(source.tag());
        return prepared;
    }

    private static void emitOverlayQuad(
            QuadEmitter emitter,
            BufferedQuad source,
            BakedQuad replacement,
            int tint) {
        QuadEmitter output =
                prepareEmission(
                        emitter,
                        source,
                        replacement);
        // 中文：Fusion 1.3.12 Fabric 在发射时才解析颜色；固定 overlay ARGB 直接写入顶点色。
        // English: Fusion 1.3.12 Fabric resolves colors at emission; write the
        // fixed overlay ARGB into vertex colors.
        for (int vertex = 0;
                vertex < 4;
                vertex++) {
            output.color(vertex, tint);
        }
        output.emit();
    }

    private static ConnectionMethod resolveMethod(
            EngineQuerySelection selection,
            BlockState state,
            FaceSurface inferenceSurface) {
        boolean requestedAuto = selection.resolution()
                .map(value -> value.method()
                        .requestedMethod()
                        == ConnectionMethod.AUTO)
                .orElse(selection.method()
                        == ConnectionMethod.AUTO);
        if (requestedAuto) {
            return selection.preparedMethods()
                    .method(
                            state,
                            inferenceSurface.direction(),
                            inferenceSurface.sprite()
                                    .contents()
                                    .name())
                    .orElse(ConnectionMethod.NONE);
        }
        return selection.method();
    }

    private Optional<FusionNativeQuadProcessor> processor(
            BakedQuad quad,
            TextureAtlasSprite sprite,
            Block block,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            Optional<Integer> overlayTint,
            ReloadPublication.Generation generation,
            RuleRuntime.Snapshot rules) {
        ProcessorKey key = new ProcessorKey(
                rules,
                quad,
                sprite,
                block,
                method,
                overlayProfile,
                overlayTint);
        return processors.computeIfAbsent(
                key,
                ignored -> createProcessor(
                        quad,
                        sprite,
                        block,
                        method,
                        overlayProfile,
                        overlayTint,
                        generation,
                        rules));
    }

    private static Optional<FusionNativeQuadProcessor> createProcessor(
            BakedQuad quad,
            TextureAtlasSprite sprite,
            Block block,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            Optional<Integer> overlayTint,
            ReloadPublication.Generation generation,
            RuleRuntime.Snapshot rules) {
        return FusionGeneratedStateSprites
                .sprites(
                        generation,
                        sprite,
                        method,
                        overlayProfile)
                .flatMap(stateSprites ->
                        FusionNativeQuadProcessor
                                .create(
                                        quad,
                                        sprite,
                                        stateSprites,
                                        block,
                                        rules.rules(),
                                        method,
                                        overlayTint));
    }

    private static List<Donor> selectDonors(
            BlockAndTintGetter level,
            BlockPos pos,
            Direction face,
            BlockState receiver,
            com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet<Block>
                    rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        return OverlayDonorResolution.resolveAll(
                level,
                pos,
                face,
                receiver,
                rules,
                surfaces,
                EngineFamily.FUSION,
                OverlayDonorResolution.planarDirections(face));
    }

    private static final class BufferingEmitter
            implements QuadEmitter {
        private final QuadEmitter delegate;
        private final SpriteFinder spriteFinder;
        private final ArrayList<BufferedQuad> quads =
                new ArrayList<>();

        private BufferingEmitter(
                SpriteFinder spriteFinder) {
            this.spriteFinder = Objects.requireNonNull(
                    spriteFinder,
                    "spriteFinder");
            // 中文：真实 Renderer emitter 在 emit() 时先应用 pushTransform 的
            // Fusion QuadTransform 栈，再把 transform 后的 quad 交给本回调；
            // 回调负责把它转成 BakedQuad 并记入 quads，避免捕获 transform 前状态。
            // English: The real Renderer emitter applies the pushed Fusion
            // QuadTransform stack on emit() and then hands the transformed quad
            // to this callback; the callback converts it to a BakedQuad and
            // records it, so the pre-transform state is never captured.
            this.delegate = Objects.requireNonNull(
                    Renderer.get().quadEmitter(emitted -> {
                        TextureAtlasSprite sprite =
                                spriteFinder.find(emitted);
                        // 中文：toBakedQuad 前捕获 transform 后的 cull/nominal/tag；
                        // 26.1.2 BakedQuad 不保留 cullFace，重发射前必须恢复。
                        // English: capture the transformed cull/nominal/tag before
                        // toBakedQuad; the 26.1.2 BakedQuad drops cullFace, which
                        // must be restored before re-emission.
                        quads.add(
                                new BufferedQuad(
                                        emitted.toBakedQuad(
                                                sprite),
                                        emitted.cullFace(),
                                        emitted.nominalFace(),
                                        emitted.tag()));
                    }),
                    "delegate");
        }

        private List<BufferedQuad> quads() {
            return List.copyOf(quads);
        }

        @Override
        public void buffer(
                int light,
                com.mojang.blaze3d.vertex.VertexConsumer consumer) {
            delegate.buffer(light, consumer);
        }

        @Override
        public void buffer(
                int light,
                com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                com.mojang.blaze3d.vertex.VertexConsumer consumer) {
            delegate.buffer(light, pose, consumer);
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
        public float posByIndex(int vertexIndex, int coordinateIndex) {
            return delegate.posByIndex(vertexIndex, coordinateIndex);
        }

        @Override
        public org.joml.Vector3f copyPos(
                int vertexIndex,
                org.joml.Vector3f target) {
            return delegate.copyPos(vertexIndex, target);
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
            return delegate.copyUv(vertexIndex, target);
        }

        @Override
        public int lightmap(int vertexIndex) {
            return delegate.lightmap(vertexIndex);
        }

        @Override
        public boolean hasNormal(int vertexIndex) {
            return delegate.hasNormal(vertexIndex);
        }

        @Override
        public float normalX(int vertexIndex) {
            return delegate.normalX(vertexIndex);
        }

        @Override
        public float normalY(int vertexIndex) {
            return delegate.normalY(vertexIndex);
        }

        @Override
        public float normalZ(int vertexIndex) {
            return delegate.normalZ(vertexIndex);
        }

        @Override
        public org.joml.Vector3f copyNormal(
                int vertexIndex,
                org.joml.Vector3f target) {
            return delegate.copyNormal(vertexIndex, target);
        }

        @Override
        public org.joml.Vector3fc faceNormal() {
            return delegate.faceNormal();
        }

        @Override
        public net.minecraft.core.Direction lightFace() {
            return delegate.lightFace();
        }

        @Override
        public net.minecraft.core.Direction nominalFace() {
            return delegate.nominalFace();
        }

        @Override
        public net.minecraft.core.Direction cullFace() {
            return delegate.cullFace();
        }

        @Override
        public net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas atlas() {
            return delegate.atlas();
        }

        @Override
        public net.minecraft.client.renderer.chunk.ChunkSectionLayer chunkLayer() {
            return delegate.chunkLayer();
        }

        @Override
        public net.minecraft.client.renderer.rendertype.RenderType itemRenderType() {
            return delegate.itemRenderType();
        }

        @Override
        public boolean emissive() {
            return delegate.emissive();
        }

        @Override
        public boolean diffuseShade() {
            return delegate.diffuseShade();
        }

        @Override
        public net.fabricmc.fabric.api.util.TriState ambientOcclusion() {
            return delegate.ambientOcclusion();
        }

        @Override
        public net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foilType() {
            return delegate.foilType();
        }

        @Override
        public net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode shadeMode() {
            return delegate.shadeMode();
        }

        @Override
        public boolean animated() {
            return delegate.animated();
        }

        @Override
        public int tintIndex() {
            return delegate.tintIndex();
        }

        @Override
        public int tag() {
            return delegate.tag();
        }

        @Override
        public QuadEmitter emit() {
            delegate.emit();
            return this;
        }

        @Override
        public QuadEmitter pos(
                int vertexIndex,
                float x,
                float y,
                float z) {
            delegate.pos(vertexIndex, x, y, z);
            return this;
        }

        @Override
        public QuadEmitter color(
                int vertexIndex,
                int color) {
            delegate.color(vertexIndex, color);
            return this;
        }

        @Override
        public QuadEmitter uv(
                int vertexIndex,
                float u,
                float v) {
            delegate.uv(vertexIndex, u, v);
            return this;
        }

        @Override
        public QuadEmitter lightmap(
                int vertexIndex,
                int light) {
            delegate.lightmap(vertexIndex, light);
            return this;
        }

        @Override
        public QuadEmitter normal(
                int vertexIndex,
                float x,
                float y,
                float z) {
            delegate.normal(vertexIndex, x, y, z);
            return this;
        }

        @Override
        public QuadEmitter nominalFace(Direction face) {
            delegate.nominalFace(face);
            return this;
        }

        @Override
        public QuadEmitter cullFace(Direction face) {
            delegate.cullFace(face);
            return this;
        }

        @Override
        public QuadEmitter atlas(
                net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas
                        atlas) {
            delegate.atlas(atlas);
            return this;
        }

        @Override
        public QuadEmitter chunkLayer(
                net.minecraft.client.renderer.chunk.ChunkSectionLayer
                        layer) {
            delegate.chunkLayer(layer);
            return this;
        }

        @Override
        public QuadEmitter itemRenderType(
                net.minecraft.client.renderer.rendertype.RenderType
                        renderType) {
            delegate.itemRenderType(renderType);
            return this;
        }

        @Override
        public QuadEmitter emissive(boolean emissive) {
            delegate.emissive(emissive);
            return this;
        }

        @Override
        public QuadEmitter diffuseShade(boolean diffuse) {
            delegate.diffuseShade(diffuse);
            return this;
        }

        @Override
        public QuadEmitter ambientOcclusion(
                net.fabricmc.fabric.api.util.TriState ao) {
            delegate.ambientOcclusion(ao);
            return this;
        }

        @Override
        public QuadEmitter foilType(
                net.minecraft.client.renderer.item.ItemStackRenderState.FoilType
                        foilType) {
            delegate.foilType(foilType);
            return this;
        }

        @Override
        public QuadEmitter shadeMode(
                net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode
                        shadeMode) {
            delegate.shadeMode(shadeMode);
            return this;
        }

        @Override
        public QuadEmitter animated(boolean animated) {
            delegate.animated(animated);
            return this;
        }

        @Override
        public QuadEmitter tintIndex(int tintIndex) {
            delegate.tintIndex(tintIndex);
            return this;
        }

        @Override
        public QuadEmitter tag(int tag) {
            delegate.tag(tag);
            return this;
        }

        @Override
        public QuadEmitter copyFrom(
                net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView
                        quad) {
            delegate.copyFrom(quad);
            return this;
        }

        @Override
        public QuadEmitter fromBakedQuad(BakedQuad quad) {
            delegate.fromBakedQuad(quad);
            return this;
        }

        @Override
        public QuadEmitter clear() {
            delegate.clear();
            return this;
        }

        @Override
        public void pushTransform(
                net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform
                        transform) {
            delegate.pushTransform(transform);
        }

        @Override
        public void popTransform() {
            delegate.popTransform();
        }
    }

    private record BufferedQuad(
            BakedQuad quad,
            Direction cullFace,
            Direction nominalFace,
            int tag) {}

    private record ProcessorKey(
            RuleRuntime.Snapshot ruleSnapshot,
            BakedQuad quad,
            TextureAtlasSprite sprite,
            Block block,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            Optional<Integer> overlayTint) {}
}
