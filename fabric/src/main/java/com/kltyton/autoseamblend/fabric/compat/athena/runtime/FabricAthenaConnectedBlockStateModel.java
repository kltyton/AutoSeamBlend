package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPhysicalTilePlan;
import com.kltyton.autoseamblend.compat.athena.plan.AthenaMethodPolicy;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeStateSampler;
import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.fabric.runtime.render.FabricQuadEmitting;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.overlay.PlanarOverlayNeighborhood;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.render.DonorTintResolver;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftTopSurfaceResolver;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import earth.terrarium.athena.api.client.fabric.WrappedGetter;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * 中文：Athena 支持的动态模型；每次运行时发射都采样 Athena 原生 CTM 状态并发射四个
 * 原生等价象限 Quad。
 *
 * English: Athena-backed dynamic model that samples Athena's native CTM state on every runtime
 * emission and emits the four native-equivalent quadrant quads.
 */
public final class FabricAthenaConnectedBlockStateModel
        extends ForwardingBakedModel {
    private final BlockState bakedState;

    public FabricAthenaConnectedBlockStateModel(
            BakedModel delegate,
            BlockState bakedState) {
        super(Objects.requireNonNull(
                delegate,
                "delegate"));
        this.bakedState = Objects.requireNonNull(
                bakedState,
                "bakedState");
    }

    @Override
    public void emitBlockQuads(
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            Supplier<RandomSource> randomSupplier,
            RenderContext context) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(randomSupplier, "randomSupplier");
        Objects.requireNonNull(context, "context");
        if (state != bakedState) {
            super.emitBlockQuads(
                    level,
                    state,
                    pos,
                    randomSupplier,
                    context);
            return;
        }
        ReloadPublication.read(generation -> {
            emitBlockQuads(
                    generation,
                    level,
                    state,
                    pos,
                    randomSupplier,
                    context);
            return null;
        });
    }

    private void emitBlockQuads(
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            Supplier<RandomSource> randomSupplier,
            RenderContext context) {
        RandomSource random = randomSupplier.get();
        long seed = random.nextLong();
        // 中文：每次捕获按当前方块图集解析一次 SpriteFinder，只由本次短生命周期的
        // BufferingEmitter 持有；资源重载重新缝合图集后不会残留旧实例。
        // English: Resolves one SpriteFinder against the current block atlas per capture,
        // held only by this short-lived BufferingEmitter, so no stale instance survives a
        // resource reload that re-stitches the atlas.
        SpriteFinder finder = SpriteFinder.get(
                Minecraft.getInstance()
                        .getModelManager()
                        .getAtlas(
                                TextureAtlas
                                        .LOCATION_BLOCKS));
        BufferingEmitter buffer =
                new BufferingEmitter(
                        RendererAccess.INSTANCE
                                .getRenderer()
                                .meshBuilder()
                                .getEmitter(),
                        finder);
        super.emitBlockQuads(
                level,
                state,
                pos,
                () -> RandomSource.create(seed),
                new CapturingRenderContext(
                        buffer));
        RuleRuntime.Snapshot rules =
                generation.selectors();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                generation.surfaces();
        QuadEmitter output = context.getEmitter();
        for (BakedQuad quad : buffer.quads()) {
            emitQuad(
                    generation,
                    level,
                    pos,
                    state,
                    rules,
                    surfaces,
                    output,
                    quad);
        }
    }

    private static void emitQuad(
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            RuleRuntime.Snapshot rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            QuadEmitter output,
            BakedQuad quad) {
        Direction face = quad.getDirection();
        if (face == null) {
            passthrough(output, quad);
            return;
        }
        TextureAtlasSprite sprite = quad.getSprite();
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
        EngineQuerySelection selected = selection.orElseThrow();
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
            Optional<TextureAtlasSprite> topSprite = MinecraftTopSurfaceResolver
                    .resolve(
                            level,
                            pos,
                            state,
                            face,
                            rules.rules(),
                            surfaces);
            if (topSprite.isPresent()) {
                FabricAthenaNativeQuadProcessor.emitDirectReplacement(
                        face,
                        sprite,
                        topSprite.orElseThrow(),
                        quad.getTintIndex(),
                        output);
            } else {
                passthrough(output, quad);
            }
            return;
        }
        if (AthenaMethodPolicy.replacement(method)) {
            Optional<TextureAtlasSprite[]> stateSprites =
                    AthenaGeneratedStateSprites.sprites(
                            generation,
                            surface.sprite(),
                            method,
                            surface.overlayProfile());
            if (stateSprites.isEmpty()) {
                passthrough(output, quad);
                return;
            }
            boolean completesPhysicalSlots =
                    selected.nativeSlots()
                            .stream()
                            .anyMatch(slot ->
                                    slot.intent()
                                            .fillable());
            if (completesPhysicalSlots
                    && missing(sprite)) {
                CtmState nativeState = AthenaNativeStateSampler.sample(
                        new WrappedGetter(level),
                        state,
                        pos,
                        face,
                        rules.rules(),
                        Set.of());
                int slot = AthenaPhysicalTilePlan
                        .roleFor(
                                AthenaNativeStateSampler
                                        .connections(nativeState))
                        .nativeIndex();
                TextureAtlasSprite[] sprites =
                        stateSprites.orElseThrow();
                if (slot < 0
                        || slot >= sprites.length
                        || sprites[slot] == null) {
                    passthrough(output, quad);
                    return;
                }
                FabricAthenaNativeQuadProcessor.emitDirectReplacement(
                        face,
                        sprite,
                        sprites[slot],
                        quad.getTintIndex(),
                        output);
                return;
            }
            // 中文：源 quad + fullFace 交给处理器：fullFace 走象限路径（草/完整玻璃），
            // !fullFace（pane 薄条等）走 retexture 保留几何与本地 UV。
            // English: The source quad plus fullFace go to the processor: fullFace keeps the
            // quadrant path (grass/full glass) while !fullFace (pane strips and other
            // secondary faces) takes retexture, preserving geometry and local UVs.
            boolean emitted =
                    FabricAthenaNativeQuadProcessor.emitReplacement(
                            face,
                            sprite,
                            stateSprites.orElseThrow(),
                            level,
                            pos,
                            state,
                            rules.rules(),
                            quad,
                            quad.getTintIndex(),
                            output,
                            OptionalInt.empty(),
                            method,
                            surface.fullFace());
            if (!emitted) {
                passthrough(output, quad);
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
        if (outward.isSolidRender(
                level,
                pos.relative(face))) {
            return;
        }
        List<Donor> donors = OverlayDonorResolution.resolveAll(
                level,
                pos,
                face,
                state,
                rules.rules(),
                surfaces,
                EngineFamily.ATHENA,
                PlanarOverlayNeighborhood.planarDirections(face));
        for (Donor donor : donors) {
            int tint = DonorTintResolver.resolve(
                    donor.state(),
                    level,
                    pos,
                    donor.surface().tintIndex());
            Optional<TextureAtlasSprite[]> donorSprites =
                    AthenaGeneratedStateSprites.sprites(
                            generation,
                            donor.surface().sprite(),
                            donor.method(),
                            donor.surface()
                                    .overlayProfile());
            if (donorSprites.isEmpty()) {
                continue;
            }
            CtmState nativeState =
                    FabricAthenaNativeOverlayStateSampler.state(
                            level,
                            pos,
                            state,
                            donor,
                            face,
                            rules.rules(),
                            surfaces);
            if (nativeState.allTrue()) {
                continue;
            }
            // 中文：donor 循环已算出 overlay 状态，必须直接作为 AthenaNativeProvider.quads
            // 的状态输入，禁止丢弃后对接收方块重新普通采样。
            // English: The donor loop has already computed the overlay state; it must be the
            // direct AthenaNativeProvider.quads input and must not be discarded for a plain
            // receiver re-sample.
            FabricAthenaNativeQuadProcessor.emitOverlayReplacement(
                    face,
                    nativeState,
                    donorSprites.orElseThrow(),
                    tint,
                    output);
        }
    }

    private static void passthrough(
            QuadEmitter output,
            BakedQuad quad) {
        FabricQuadEmitting.fromBakedQuad(
                        output,
                        quad)
                .emit();
    }

    private static boolean missing(
            TextureAtlasSprite sprite) {
        return sprite == null
                || sprite.contents().name().equals(
                        MissingTextureAtlasSprite.getLocation());
    }

    /**
     * 中文：把 Athena 发射通道的 quad 捕获为 BakedQuad；FRAPI 1.21.1 用方块图集
     * SpriteFinder 反查精灵。
     *
     * English: Captures quads emitted by the Athena pass as BakedQuads; FRAPI 1.21.1 resolves
     * the sprite through the block-atlas SpriteFinder.
     */
    private static final class CapturingRenderContext
            implements RenderContext {
        private final QuadEmitter emitter;

        private CapturingRenderContext(
                QuadEmitter emitter) {
            this.emitter = Objects.requireNonNull(
                    emitter,
                    "emitter");
        }

        @Override
        public QuadEmitter getEmitter() {
            return emitter;
        }

        @Override
        public void pushTransform(
                RenderContext.QuadTransform transform) {
        }

        @Override
        public void popTransform() {
        }

        @Override
        public RenderContext.BakedModelConsumer
                bakedModelConsumer() {
            return new RenderContext.BakedModelConsumer() {
                @Override
                public void accept(BakedModel model) {
                }

                @Override
                public void accept(
                        BakedModel model,
                        BlockState state) {
                }
            };
        }
    }

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
            TextureAtlasSprite sprite =
                    finder.find(delegate);
            quads.add(
                    delegate.toBakedQuad(
                            sprite));
            return this;
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
        public Vector3f faceNormal() {
            return delegate.faceNormal();
        }

        @Override
        public RenderMaterial material() {
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
                int[] vertices,
                int vertexIndex) {
            delegate.toVanilla(
                    vertices,
                    vertexIndex);
        }

        @Override
        public QuadEmitter pos(
                int vertexIndex,
                float x,
                float y,
                float z) {
            delegate.pos(
                    vertexIndex,
                    x,
                    y,
                    z);
            return this;
        }

        @Override
        public QuadEmitter color(
                int vertexIndex,
                int color) {
            delegate.color(
                    vertexIndex,
                    color);
            return this;
        }

        @Override
        public QuadEmitter uv(
                int vertexIndex,
                float u,
                float v) {
            delegate.uv(
                    vertexIndex,
                    u,
                    v);
            return this;
        }

        @Override
        public QuadEmitter spriteBake(
                TextureAtlasSprite sprite,
                int bakeFlags) {
            delegate.spriteBake(
                    sprite,
                    bakeFlags);
            return this;
        }

        @Override
        public QuadEmitter lightmap(
                int vertexIndex,
                int light) {
            delegate.lightmap(
                    vertexIndex,
                    light);
            return this;
        }

        @Override
        public QuadEmitter normal(
                int vertexIndex,
                float x,
                float y,
                float z) {
            delegate.normal(
                    vertexIndex,
                    x,
                    y,
                    z);
            return this;
        }

        @Override
        public QuadEmitter cullFace(Direction face) {
            delegate.cullFace(face);
            return this;
        }

        @Override
        public QuadEmitter nominalFace(
                Direction face) {
            delegate.nominalFace(face);
            return this;
        }

        @Override
        public QuadEmitter material(
                RenderMaterial material) {
            delegate.material(material);
            return this;
        }

        @Override
        public QuadEmitter colorIndex(int colorIndex) {
            delegate.colorIndex(colorIndex);
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
        public QuadEmitter fromVanilla(
                int[] vertices,
                int vertexIndex) {
            delegate.fromVanilla(
                    vertices,
                    vertexIndex);
            return this;
        }

        @Override
        public QuadEmitter fromVanilla(
                BakedQuad quad,
                RenderMaterial material,
                Direction cullFace) {
            delegate.fromVanilla(
                    quad,
                    material,
                    cullFace);
            return this;
        }
    }
}
