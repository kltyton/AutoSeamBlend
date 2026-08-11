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
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
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
        // 1.20.1 ForwardingBakedModel has a no-arg ctor and a protected wrapped field.
        this.wrapped = Objects.requireNonNull(
                delegate,
                "delegate");
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
        RandomSource captureRandom =
                RandomSource.create(seed);
        // 中文：每次捕获按当前方块图集解析一次 SpriteFinder，只由本次短生命周期的
        // capture transform 持有；资源重载重新缝合图集后不会残留旧实例。
        // English: Resolves one SpriteFinder against the current block atlas per capture,
        // held only by this short-lived capture transform, so no stale instance survives a
        // resource reload that re-stitches the atlas.
        SpriteFinder finder = SpriteFinder.get(
                Minecraft.getInstance()
                        .getModelManager()
                        .getAtlas(
                                TextureAtlas
                                        .LOCATION_BLOCKS));
        ArrayList<CapturedQuad> capturedQuads =
                new ArrayList<>();
        // 中文：原版 BakedModel.emitBlockQuads 会把 context 转成 Indigo 的真实方块
        // 渲染上下文，因此不能传入自制 RenderContext。捕获 transform 必须装到调用方传入
        // 的真实栈上；记录最终 quad 后返回 false，pop 后再回放。
        // English: Vanilla BakedModel.emitBlockQuads casts the context to Indigo's real
        // block render context, so a fabricated RenderContext is invalid. Install capture
        // on the incoming real stack, record the final quad, return false, then replay after
        // the transform has been popped.
        RenderContext.QuadTransform capture = quad -> {
            capturedQuads.add(
                    new CapturedQuad(
                            quad.toBakedQuad(
                                    finder.find(quad)),
                            quad.material(),
                            quad.cullFace(),
                            quad.nominalFace(),
                            quad.tag()));
            return false;
        };
        context.pushTransform(capture);
        try {
            super.emitBlockQuads(
                    level,
                    state,
                    pos,
                    () -> captureRandom,
                    context);
        } finally {
            context.popTransform();
        }
        RuleRuntime.Snapshot rules =
                generation.selectors();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                generation.surfaces();
        QuadEmitter output = context.getEmitter();
        for (CapturedQuad captured : capturedQuads) {
            emitQuad(
                    generation,
                    level,
                    pos,
                    state,
                    rules,
                    surfaces,
                    output,
                    captured);
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
            CapturedQuad captured) {
        BakedQuad quad = captured.quad();
        Direction face = quad.getDirection();
        if (face == null) {
            passthrough(output, captured);
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
            passthrough(output, captured);
            return;
        }
        Optional<FaceSurface> faceSurface =
                surfaces.face(
                        state,
                        face,
                        sprite);
        if (faceSurface.isEmpty()) {
            passthrough(output, captured);
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
                passthrough(output, captured);
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
                passthrough(output, captured);
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
                    passthrough(output, captured);
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
                passthrough(output, captured);
            }
            return;
        }
        passthrough(output, captured);
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
            CapturedQuad captured) {
        QuadEmitter prepared =
                FabricQuadEmitting.fromBakedQuad(
                        output,
                        captured.quad(),
                        captured.material(),
                        captured.cullFace());
        prepared.nominalFace(
                captured.nominalFace());
        prepared.tag(captured.tag());
        prepared.emit();
    }

    private static boolean missing(
            TextureAtlasSprite sprite) {
        return sprite == null
                || sprite.contents().name().equals(
                        MissingTextureAtlasSprite.getLocation());
    }

    /**
     * 中文：BakedQuad 不携带 FRAPI material/cullFace/nominalFace/tag；真实 context 捕获后
     * 必须单独保存这些发射事实，供 vanilla passthrough 精确恢复。
     *
     * <p>English: BakedQuad does not retain FRAPI material/cullFace/nominalFace/tag; these
     * emission facts must be captured separately from the real context and restored for
     * exact vanilla passthrough replay.
     */
    private record CapturedQuad(
            BakedQuad quad,
            RenderMaterial material,
            Direction cullFace,
            Direction nominalFace,
            int tag) {}

}
