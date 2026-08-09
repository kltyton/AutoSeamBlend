package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.plan.AthenaMethodPolicy;
import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.overlay.PlanarOverlayNeighborhood;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.render.DonorTintResolver;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftTopSurfaceResolver;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricTextureAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 中文：Athena 支持的动态模型。每次运行时发射都通过 ReloadPublication.read 捕获根代次，
 * 先缓存 delegate（Athena 原生烘焙模型）的 Quad，再按 EngineQueryRouter 精确查询、
 * face+sprite 表面绑定与 resolveMethod 一次完成 TOP/replacement/overlay 路由；非目标或
 * 无表面 Quad 原样透传。移植 1.21.1 ce33d6c 的路由/生命周期修复与 26.1.2 NeoForge
 * AthenaConnectedBlockStateModel 的精确查询语义。
 *
 * English: Athena-backed dynamic model. Every runtime emission captures the root generation
 * under ReloadPublication.read, buffers the delegate (native Athena baked model) quads, then
 * routes TOP/replacement/overlay through EngineQueryRouter exact queries, face+sprite surface
 * binding, and a single resolveMethod; non-target or unbounded quads pass through unchanged.
 * Ports the 1.21.1 ce33d6c routing/lifecycle fixes and the 26.1.2 NeoForge
 * AthenaConnectedBlockStateModel exact-query semantics.
 */
public final class FabricAthenaConnectedBlockStateModel
        extends WrapperBlockStateModel {
    private final BlockState bakedState;

    public FabricAthenaConnectedBlockStateModel(
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
        Objects.requireNonNull(emitter, "emitter");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(cullTest, "cullTest");
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
        // 中文：每次捕获按当前方块图集解析一次 SpriteFinder，只由本次短生命周期的
        // BufferingEmitter 持有；资源重载重新缝合图集后不会残留旧实例。scratch 与 buffer
        // 共用同一个渲染器 emitter，捕获结束后复用为原生 Quad 烘焙工作区。
        // English: Resolves one SpriteFinder against the current block atlas per capture,
        // held only by this short-lived BufferingEmitter, so no stale instance survives a
        // resource reload that re-stitches the atlas. The scratch and buffer share one
        // renderer emitter; after capture it is reused as the native-quad bake work area.
        TextureAtlas atlas = Minecraft.getInstance()
                .getAtlasManager()
                .getAtlasOrThrow(
                        AtlasIds.BLOCKS);
        SpriteFinder finder =
                ((FabricTextureAtlas) atlas)
                        .spriteFinder();
        QuadEmitter scratch = Renderer.get()
                .quadEmitter(ignored -> {});
        BufferingEmitter buffer =
                new BufferingEmitter(
                        scratch,
                        finder);
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
        for (BakedQuad quad : buffer.quads()) {
            emitQuad(
                    generation,
                    scratch,
                    emitter,
                    level,
                    pos,
                    state,
                    rules.rules(),
                    surfaces,
                    quad);
        }
    }

    private static void emitQuad(
            ReloadPublication.Generation generation,
            QuadEmitter scratch,
            QuadEmitter output,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            BakedQuad quad) {
        Direction face = quad.direction();
        if (face == null) {
            passthrough(output, quad);
            return;
        }
        TextureAtlasSprite sprite =
                quad.materialInfo().sprite();
        if (sprite == null) {
            passthrough(output, quad);
            return;
        }
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
                != EngineFamily.ATHENA
                || !selection.orElseThrow()
                .runsAutoBlend()) {
            passthrough(output, quad);
            return;
        }
        Optional<FaceSurface> faceSurface =
                surfaces.face(
                        state,
                        face,
                        sprite);
        if (faceSurface.isEmpty()) {
            passthrough(output, quad);
            return;
        }
        FaceSurface surface = faceSurface.orElseThrow();
        FaceSurface inferenceSurface = surfaces
                .preferredFace(
                        state,
                        surface.direction())
                .orElse(surface);
        EngineQuerySelection selected =
                selection.orElseThrow();
        // 中文：auto 在本次精确查询内解析一次；Athena 运行时与首轮 Atlas 规划消费同一具体方法。
        // English: Resolve auto once for this exact query so Athena runtime and initial atlas
        // planning consume the same concrete method.
        ConnectionMethod method = selected.resolveMethod(
                state,
                inferenceSurface.direction(),
                inferenceSurface.sprite()
                        .contents()
                        .name());
        if (method == ConnectionMethod.TOP) {
            Optional<TextureAtlasSprite> topSprite =
                    MinecraftTopSurfaceResolver
                            .resolve(
                                    level,
                                    pos,
                                    state,
                                    face,
                                    rules,
                                    surfaces);
            if (topSprite.isPresent()) {
                emit(
                        output,
                        FabricAthenaNativeQuadProcessor
                                .retexture(
                                        quad,
                                        topSprite.orElseThrow()));
            } else {
                passthrough(output, quad);
            }
            return;
        }
        if (AthenaMethodPolicy.replacement(method)) {
            boolean completesPhysicalSlots =
                    selected.nativeSlots()
                            .stream()
                            .anyMatch(slot ->
                                    slot.intent()
                                            .fillable());
            Optional<TextureAtlasSprite[]>
                    stateSprites =
                    AthenaGeneratedStateSprites
                            .sprites(
                                    generation,
                                    surface.sprite(),
                                    method);
            if (completesPhysicalSlots) {
                // 中文：槽位补全只针对缺失精灵；作者已提供的原生 Quad 必须原样保留。
                // English: Slot completion applies only to missing sprites; author-provided
                // native quads must be preserved as-is.
                if (!missing(sprite)) {
                    passthrough(output, quad);
                    return;
                }
                Optional<BakedQuad> replacement =
                        stateSprites.flatMap(sprites ->
                                FabricAthenaNativeQuadProcessor
                                        .completeMissing(
                                                quad,
                                                sprites,
                                                level,
                                                pos,
                                                state,
                                                rules));
                if (replacement.isPresent()) {
                    emit(
                            output,
                            replacement.orElseThrow());
                } else {
                    passthrough(output, quad);
                }
                return;
            }
            if (stateSprites.isEmpty()) {
                passthrough(output, quad);
                return;
            }
            List<BakedQuad> replacements =
                    FabricAthenaNativeQuadProcessor
                            .process(
                                    quad,
                                    stateSprites.orElseThrow(),
                                    level,
                                    pos,
                                    state,
                                    rules);
            if (replacements.isEmpty()) {
                passthrough(output, quad);
            } else {
                replacements.forEach(replacement ->
                        emit(output, replacement));
            }
            return;
        }
        passthrough(output, quad);
        if (!AthenaMethodPolicy.overlay(method)
                || !surface.fullFace()
                || !surface.facts()
                .alphaOpaque()
                .isTrue()) {
            return;
        }
        BlockState outward = level.getBlockState(
                pos.relative(face));
        if (outward.isSolidRender()) {
            // 中文：完整实心邻块已遮住接收面；在供体选择和两轮 8 邻域采样前退出，避免不可见地下表面的无效工作。
            // English: A fully solid neighbor already occludes the receiver face; exit before donor selection and both eight-neighbor sampling passes.
            return;
        }
        List<Donor> donors =
                OverlayDonorResolution.resolveAll(
                        level,
                        pos,
                        face,
                        state,
                        rules,
                        surfaces,
                        EngineFamily.ATHENA,
                        PlanarOverlayNeighborhood
                                .planarDirections(
                                        face));
        for (Donor donor : donors) {
            int tint = DonorTintResolver.resolve(
                    donor.state(),
                    level,
                    pos,
                    donor.surface().tintIndex());
            Optional<TextureAtlasSprite[]>
                    donorSprites =
                    AthenaGeneratedStateSprites
                            .sprites(
                                    generation,
                                    donor.surface()
                                            .sprite(),
                                    donor.method(),
                                    donor.surface()
                                            .overlayProfile());
            if (donorSprites.isEmpty()) {
                continue;
            }
            List<BakedQuad> overlays =
                    FabricAthenaNativeQuadProcessor
                            .processOverlay(
                                    scratch,
                                    quad,
                                    donorSprites.orElseThrow(),
                                    level,
                                    pos,
                                    state,
                                    rules,
                                    surface.fullFace(),
                                    new FabricAthenaNativeQuadProcessor
                                            .OverlayRequest(
                                                    donor,
                                                    surfaces,
                                                    tint));
            overlays.forEach(overlay -> {
                FabricAthenaNativeQuadProcessor
                        .emitOverlayTinted(
                                output,
                                overlay,
                                tint);
            });
        }
    }

    private static void emit(
            QuadEmitter output,
            BakedQuad replacement) {
        output.fromBakedQuad(replacement).emit();
    }

    private static void passthrough(
            QuadEmitter output,
            BakedQuad quad) {
        output.fromBakedQuad(quad).emit();
    }

    private static boolean missing(
            TextureAtlasSprite sprite) {
        return sprite == null
                || sprite.contents().name().equals(
                        MissingTextureAtlasSprite
                                .getLocation());
    }

    /**
     * 中文：把 Athena 发射通道的 quad 捕获为 BakedQuad；26.1 FRAPI 用方块图集 SpriteFinder
     * 反查精灵，与 FabricFusionConnectedBlockStateModel.BufferingEmitter 相同接口契约。
     *
     * English: Captures quads emitted by the Athena pass as BakedQuads; 26.1 FRAPI resolves
     * the sprite through the block-atlas SpriteFinder, with the same interface contract as
     * FabricFusionConnectedBlockStateModel.BufferingEmitter.
     */
    private static final class BufferingEmitter
            implements QuadEmitter {
        private final QuadEmitter delegate;
        private final SpriteFinder finder;
        private final ArrayList<BakedQuad> quads =
                new ArrayList<>();

        private BufferingEmitter(
                QuadEmitter delegate,
                SpriteFinder finder) {
            this.delegate = Objects.requireNonNull(
                    delegate,
                    "delegate");
            this.finder = Objects.requireNonNull(
                    finder,
                    "finder");
        }

        private List<BakedQuad> quads() {
            return List.copyOf(quads);
        }

        @Override
        public QuadEmitter emit() {
            quads.add(
                    delegate.toBakedQuad(
                            finder.find(delegate)));
            delegate.emit();
            delegate.clear();
            return this;
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
        public float posByIndex(
                int vertexIndex,
                int coordinateIndex) {
            return delegate.posByIndex(
                    vertexIndex,
                    coordinateIndex);
        }

        @Override
        public Vector3f copyPos(
                int vertexIndex,
                Vector3f target) {
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
        public Vector2f copyUv(
                int vertexIndex,
                Vector2f target) {
            return delegate.copyUv(
                    vertexIndex,
                    target);
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
        public Vector3f copyNormal(
                int vertexIndex,
                Vector3f target) {
            return delegate.copyNormal(
                    vertexIndex,
                    target);
        }

        @Override
        public Vector3fc faceNormal() {
            return delegate.faceNormal();
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
        public Direction cullFace() {
            return delegate.cullFace();
        }

        @Override
        public QuadAtlas atlas() {
            return delegate.atlas();
        }

        @Override
        public ChunkSectionLayer chunkLayer() {
            return delegate.chunkLayer();
        }

        @Override
        public RenderType itemRenderType() {
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
        public TriState ambientOcclusion() {
            return delegate.ambientOcclusion();
        }

        @Override
        public ItemStackRenderState.FoilType foilType() {
            return delegate.foilType();
        }

        @Override
        public ShadeMode shadeMode() {
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
        public QuadEmitter nominalFace(
                Direction face) {
            delegate.nominalFace(face);
            return this;
        }

        @Override
        public QuadEmitter cullFace(
                Direction face) {
            delegate.cullFace(face);
            return this;
        }

        @Override
        public QuadEmitter atlas(QuadAtlas atlas) {
            delegate.atlas(atlas);
            return this;
        }

        @Override
        public QuadEmitter chunkLayer(
                ChunkSectionLayer layer) {
            delegate.chunkLayer(layer);
            return this;
        }

        @Override
        public QuadEmitter itemRenderType(
                RenderType renderType) {
            delegate.itemRenderType(renderType);
            return this;
        }

        @Override
        public QuadEmitter emissive(
                boolean emissive) {
            delegate.emissive(emissive);
            return this;
        }

        @Override
        public QuadEmitter diffuseShade(
                boolean diffuse) {
            delegate.diffuseShade(diffuse);
            return this;
        }

        @Override
        public QuadEmitter ambientOcclusion(
                TriState ao) {
            delegate.ambientOcclusion(ao);
            return this;
        }

        @Override
        public QuadEmitter foilType(
                ItemStackRenderState.FoilType foilType) {
            delegate.foilType(foilType);
            return this;
        }

        @Override
        public QuadEmitter shadeMode(
                ShadeMode shadeMode) {
            delegate.shadeMode(shadeMode);
            return this;
        }

        @Override
        public QuadEmitter animated(
                boolean animated) {
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
        public QuadEmitter copyFrom(QuadView quad) {
            delegate.copyFrom(quad);
            return this;
        }

        @Override
        public QuadEmitter fromBakedQuad(
                BakedQuad quad) {
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
}
