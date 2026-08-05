package com.kltyton.autoseamblend.neoforge.compat.fusion.runtime;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionBlockStateModelPartTransformer;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.runtime.geometry.IdentityPreservingListBuilder;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftTopSurfaceResolver;
import com.kltyton.autoseamblend.runtime.render.DonorTintResolver;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionNativeQuadProcessor;
import com.kltyton.autoseamblend.compat.fusion.runtime.texture.FusionGeneratedStateSprites;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionSheetMethodPlan;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

/**
 * 中文：由 Fusion 支持的动态模型；Fusion 决定纹理方向和八邻域状态，AutoBlend 只选择已解析方法并把不可变补丁计划应用到 Fusion 可变 Quad。
 * <p>
 * English:
 * Fusion-backed dynamic model.
 *
 * <p>Fusion determines texture orientation and eight-neighbor state. AutoBlend only selects the
 * resolved method and applies the immutable patch plan to Fusion mutable quads.
 */
final class FusionConnectedBlockStateModel
        extends DelegateBlockStateModel {
    private final BlockState bakedState;
    private final ConcurrentMap<
            ProcessorKey,
            Optional<FusionNativeQuadProcessor>>
            processors = new ConcurrentHashMap<>();

    FusionConnectedBlockStateModel(
            BlockStateModel delegate,
            BlockState bakedState) {
        super(Objects.requireNonNull(delegate, "delegate"));
        this.bakedState =
                Objects.requireNonNull(bakedState, "bakedState");
    }

    @Override
    public void collectParts(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            RandomSource random,
            List<BlockStateModelPart> output) {
        ReloadPublication.read(generation -> {
            collectParts(
                    generation,
                    level,
                    pos,
                    state,
                    random,
                    output);
            return null;
        });
    }

    private void collectParts(
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            RandomSource random,
            List<BlockStateModelPart> output) {
        if (state != bakedState) {
            super.collectParts(level, pos, state, random, output);
            return;
        }
        long seed = random.nextLong();
        ArrayList<BlockStateModelPart> base = new ArrayList<>();
        super.collectParts(
                level,
                pos,
                state,
                RandomSource.create(seed),
                base);
        RuleRuntime.Snapshot rules =
                generation.selectors();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                generation.surfaces();
        for (BlockStateModelPart part : base) {
            output.add(transformPart(
                    part,
                    generation,
                    level,
                    pos,
                    state,
                    seed,
                    rules,
                    surfaces));
        }
    }

    private BlockStateModelPart transformPart(
            BlockStateModelPart part,
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            long seed,
            RuleRuntime.Snapshot rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        return FusionBlockStateModelPartTransformer.transform(
                part,
                (source, cullBucket) -> transformQuads(
                        source,
                        cullBucket,
                        generation,
                        level,
                        pos,
                        state,
                        seed,
                        rules,
                        surfaces));
    }

    private List<BakedQuad> transformQuads(
            List<BakedQuad> source,
            Direction cullBucket,
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            long seed,
            RuleRuntime.Snapshot rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        if (source.isEmpty()) {
            return source;
        }
        IdentityPreservingListBuilder<BakedQuad> output =
                new IdentityPreservingListBuilder<>(source);
        for (int sourceIndex = 0;
             sourceIndex < source.size();
             sourceIndex++) {
            BakedQuad quad = source.get(sourceIndex);
            output.beginSource(sourceIndex);
            try {
                TextureAtlasSprite sprite =
                        quad.materialInfo().sprite();
                Optional<EngineQuerySelection> selection =
                        EngineQueryRouter
                                .select(
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
                    output.add(quad);
                    continue;
                }
                Optional<FaceSurface> face =
                        surfaces.face(
                                state,
                                quad.direction(),
                                sprite);
                // 中文：Fusion 的 TextureInstance 由精确源精灵初始化；缺少该表面时必须原样透传。
                // English: Fusion initializes its TextureInstance from the exact source sprite; a missing surface must pass through unchanged.
                if (face.isEmpty()) {
                    output.add(quad);
                    continue;
                }
                FaceSurface surface = face.orElseThrow();
                FaceSurface inferenceSurface = surfaces
                        .preferredFace(
                                state,
                                surface.direction())
                        .orElse(surface);
                // 中文：与重载期精灵规划共享一次 auto 解析，避免把项目扩展方法直接交给 Fusion 原生布局。
                // English: Share the reload-time auto resolution so the project extension is never passed directly to Fusion's native layout.
                ConnectionMethod method = resolveMethod(
                        selection.orElseThrow(),
                        state,
                        inferenceSurface);
                if (method == ConnectionMethod.TOP) {
                    BakedQuad top = MinecraftTopSurfaceResolver
                            .resolve(
                                    level,
                                    pos,
                                    state,
                                    quad.direction(),
                                    rules.rules(),
                                    surfaces)
                            .map(topSprite ->
                                    FusionNativeQuadProcessor
                                            .retexture(
                                                    quad,
                                                    topSprite))
                            .orElse(quad);
                    output.add(top);
                    continue;
                }
                if (replacementMethod(method)) {
                    Optional<FusionNativeQuadProcessor>
                            processor = processor(
                            quad,
                            surface.sprite(),
                            state.getBlock(),
                            method,
                            surface.overlayProfile(),
                            Optional.empty(),
                            generation,
                            rules);
                    if (processor.isEmpty()) {
                        output.add(quad);
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
                        output.add(quad);
                    } else {
                        output.addAll(replacements);
                    }
                    continue;
                }
                output.add(quad);
                if (!overlayMethod(method)
                        || !surface.fullFace()
                        || !surface.facts().alphaOpaque().isTrue()) {
                    continue;
                }
                boolean occlusionEarlyExit = cullBucket != null
                        && level.getBlockState(
                                pos.relative(cullBucket))
                        .isSolidRender();
                if (occlusionEarlyExit) {
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
                    appendOverlay(
                            output,
                            quad,
                            donor,
                            level,
                            pos,
                            seed,
                            generation,
                            rules);
                }
            } finally {
                output.endSource();
            }
        }
        return output.finish();
    }

    /**
     * 中文：配置与隐式 AUTO 属于复合方块面的决策；Fusion 仍绑定精确源精灵，但方法必须复用首轮准备的首选表面结果。 / English: Config and implicit AUTO are composite-face decisions; Fusion still binds the exact source sprite, while the method reuses the preferred surface result prepared before stitching.
     */
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

    private void appendOverlay(
            IdentityPreservingListBuilder<BakedQuad> output,
            BakedQuad receiver,
            Donor donor,
            BlockAndTintGetter level,
            BlockPos pos,
            long seed,
            ReloadPublication.Generation generation,
            RuleRuntime.Snapshot rules) {
        int tint = DonorTintResolver.resolve(
                donor.state(),
                level,
                pos,
                donor.surface().tintIndex());
        Optional<FusionNativeQuadProcessor> processor =
                processor(
                        receiver,
                        donor.surface().sprite(),
                        donor.state().getBlock(),
                        donor.method(),
                        donor.surface()
                                .overlayProfile(),
                        Optional.of(tint),
                        generation,
                        rules);
        if (processor.isEmpty()) {
            return;
        }
        output.addAll(
                processor.orElseThrow()
                        .process(
                                level,
                                pos,
                                donor.state(),
                                seed));
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
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        return OverlayDonorResolution.resolveAll(
                level,
                pos,
                face,
                receiver,
                rules,
                surfaces,
                EngineFamily.FUSION,
                OverlayDonorResolution
                        .planarDirections(face));
    }

    private static boolean replacementMethod(ConnectionMethod method) {
        return FusionSheetMethodPlan.isReplacement(method);
    }

    private static boolean overlayMethod(ConnectionMethod method) {
        return method.overlayCapable();
    }

    private record ProcessorKey(
            RuleRuntime.Snapshot ruleSnapshot,
            BakedQuad quad,
            TextureAtlasSprite sprite,
            Block block,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            Optional<Integer> overlayTint) {
    }

}
