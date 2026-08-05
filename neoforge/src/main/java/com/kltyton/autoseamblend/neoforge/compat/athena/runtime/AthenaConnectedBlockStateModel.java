package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
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
import com.kltyton.autoseamblend.runtime.overlay.PlanarOverlayNeighborhood;
import com.kltyton.autoseamblend.compat.athena.plan.AthenaMethodPolicy;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

/**
 * 中文：由 Athena 支持的动态模型，每次运行时查询都使用 Athena 的外观与 CTM 状态。
 * <p>
 * English:
 * Athena-backed dynamic model using Athena's appearance and CTM state for every runtime query.
 */
final class AthenaConnectedBlockStateModel
        extends DelegateBlockStateModel {
    private static final List<Direction> CULL_FACES =
            List.of(Direction.values());
    private static final List<EngineFamily> ENGINE_FAMILIES =
            List.of(EngineFamily.values());

    private final BlockState bakedState;

    AthenaConnectedBlockStateModel(
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
        boolean exactSelection = requiresExactSelection(
                generation,
                state);
        Optional<EngineQuerySelection> summary =
                EngineQueryRouter.select(
                        state,
                        generation);
        if (!exactSelection
                && !AthenaMethodPolicy.runsAthenaAutoBlend(summary)) {
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
                    rules.rules(),
                    surfaces,
                    summary,
                    exactSelection));
        }
    }

    private BlockStateModelPart transformPart(
            BlockStateModelPart part,
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            Optional<EngineQuerySelection> summary,
            boolean exactSelection) {
        LinkedHashMap<Direction, List<BakedQuad>> quads = null;
        for (Direction cullFace : CULL_FACES) {
            List<BakedQuad> source = part.getQuads(cullFace);
            List<BakedQuad> transformed = transformQuads(
                    source,
                    generation,
                    level,
                    pos,
                    state,
                    rules,
                    surfaces,
                    summary,
                    exactSelection);
            if (transformed != source && quads == null) {
                quads = new LinkedHashMap<>();
                for (Direction previous : CULL_FACES) {
                    if (previous == cullFace) {
                        break;
                    }
                    quads.put(previous, part.getQuads(previous));
                }
            }
            if (quads != null) {
                quads.put(cullFace, transformed);
            }
        }
        List<BakedQuad> source = part.getQuads(null);
        List<BakedQuad> transformed = transformQuads(
                source,
                generation,
                level,
                pos,
                state,
                rules,
                surfaces,
                summary,
                exactSelection);
        if (transformed != source && quads == null) {
            quads = new LinkedHashMap<>();
            for (Direction cullFace : CULL_FACES) {
                quads.put(cullFace, part.getQuads(cullFace));
            }
        }
        if (quads != null) {
            quads.put(null, transformed);
        }
        return quads == null
                ? part
                : new AthenaConnectedPart(part, quads);
    }

    private List<BakedQuad> transformQuads(
            List<BakedQuad> source,
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            Optional<EngineQuerySelection> summary,
            boolean exactSelection) {
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
                        exactSelection
                                ? EngineQueryRouter.select(
                                generation,
                                state,
                                level,
                                pos,
                                quad,
                                sprite)
                                : summary;
                if (selection.isEmpty()
                        || selection.orElseThrow().family()
                        != EngineFamily.ATHENA
                        || !selection.orElseThrow()
                        .runsAutoBlend()) {
                    output.add(quad);
                    continue;
                }
                EngineQuerySelection selected =
                        selection.orElseThrow();
                Optional<FaceSurface> face =
                        surfaces.face(
                                state,
                                quad.direction(),
                                sprite);
                if (face.isEmpty()) {
                    // 中文：Athena 的每个 Quad 必须只绑定它自己的原始精灵；玻璃板窄边、盖板或其他次级材质不能借用同方向的主体面。
                    // English: Every Athena quad must bind only its own source sprite; pane strips, caps, and other secondary materials must not borrow the preferred surface on that face.
                    output.add(quad);
                    continue;
                }
                FaceSurface surface = face.orElseThrow();
                FaceSurface inferenceSurface = surfaces
                        .preferredFace(
                                state,
                                surface.direction())
                        .orElse(surface);
                // 中文：auto 在一次精确查询内解析一次；Athena 运行时与首轮 Atlas 规划必须消费同一个具体方法。
                // English: Resolve auto once for this exact query so Athena runtime and initial atlas planning consume the same concrete method.
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
                                    quad.direction(),
                                    rules,
                                    surfaces);
                    BakedQuad topResult = topSprite
                            .map(value -> AthenaNativeQuadProcessor.retexture(quad, value))
                            .orElse(quad);
                    output.add(topResult);
                    continue;
                }
                if (AthenaMethodPolicy.replacement(method)) {
                    boolean completesPhysicalSlots =
                            selected.nativeSlots()
                                    .stream()
                                    .anyMatch(slot ->
                                            slot.intent()
                                                    .fillable());
                    if (completesPhysicalSlots) {
                        if (!sprite.contents()
                                .name()
                                .equals(MissingTextureAtlasSprite
                                        .getLocation())) {
                            output.add(quad);
                            continue;
                        }
                        BakedQuad replacement =
                                AthenaGeneratedStateSprites
                                        .sprites(
                                                generation,
                                                surface.sprite(),
                                                method)
                                        .flatMap(stateSprites ->
                                                AthenaNativeQuadProcessor
                                                        .completeMissing(
                                                                quad,
                                                                stateSprites,
                                                                level,
                                                                pos,
                                                                state,
                                                                rules))
                                        .orElse(quad);
                        output.add(replacement);
                        continue;
                    }
                    List<BakedQuad> replacements =
                            AthenaGeneratedStateSprites
                                    .sprites(
                                            generation,
                                            surface.sprite(),
                                            method)
                                    .map(stateSprites ->
                                            AthenaNativeQuadProcessor
                                                    .process(
                                                            quad,
                                                            stateSprites,
                                                            level,
                                                            pos,
                                                            state,
                                                            rules,
                                                            surface.fullFace(),
                                                            Optional.empty()))
                                    .orElseGet(List::of);
                    if (replacements.isEmpty()) {
                        output.add(quad);
                    } else {
                        output.addAll(replacements);
                    }
                    continue;
                }
                output.add(quad);
                if (!AthenaMethodPolicy.overlay(method)
                        || !surface.fullFace()
                        || !surface.facts().alphaOpaque().isTrue()) {
                    continue;
                }
                BlockState outward = level.getBlockState(
                        pos.relative(quad.direction()));
                if (outward.isSolidRender()) {
                    // 中文：完整实心邻块已遮住接收面；在供体选择和两轮 8 邻域采样前退出，避免不可见地下表面的无效工作。
                    // English: A fully solid neighbor already occludes the receiver face; exit before donor selection and both eight-neighbor sampling passes.
                    continue;
                }
                List<Donor> donors = selectDonors(
                        level,
                        pos,
                        quad.direction(),
                        state,
                        rules,
                        surfaces);
                for (Donor donor : donors) {
                    appendOverlay(
                            output,
                            quad,
                            state,
                            donor,
                            generation,
                            level,
                            pos,
                            rules);
                }
            } finally {
                output.endSource();
            }
        }
        return output.finish();
    }

    private static void appendOverlay(
            IdentityPreservingListBuilder<BakedQuad> output,
            BakedQuad receiver,
            BlockState receiverState,
            Donor donor,
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockPos pos,
            ConnectionRuleSet<Block> rules) {
        Optional<TextureAtlasSprite[]> stateSprites = AthenaGeneratedStateSprites
                .sprites(
                        generation,
                        donor.surface().sprite(),
                        donor.method(),
                        donor.surface()
                                .overlayProfile());
        if (stateSprites.isEmpty()) {
            return;
        }
        int tintColor = DonorTintResolver.resolve(
                donor.state(),
                level,
                pos,
                donor.surface().tintIndex());
        List<BakedQuad> overlays = AthenaNativeQuadProcessor.process(
                receiver,
                stateSprites.orElseThrow(),
                level,
                pos,
                receiverState,
                rules,
                true,
                Optional.of(new AthenaNativeQuadProcessor.OverlayRequest(
                        donor,
                        generation.surfaces(),
                        tintColor)));
        output.addAll(overlays);
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
                EngineFamily.ATHENA,
                PlanarOverlayNeighborhood.planarDirections(face));
    }

    private static boolean requiresExactSelection(
            ReloadPublication.Generation generation,
            BlockState state) {
        if (!generation.modelOwnership()
                .owners(state)
                .isEmpty()) {
            return true;
        }
        String blockId = BuiltInRegistries.BLOCK
                .getKey(state.getBlock())
                .toString();
        for (EngineFamily family : ENGINE_FAMILIES) {
            if (!generation.nativeRules()
                    .rules(family, blockId)
                    .isEmpty()
                    || !generation.managedRules()
                    .rules(family, blockId)
                    .isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private record AthenaConnectedPart(
            BlockStateModelPart delegate,
            Map<Direction, List<BakedQuad>> quads)
            implements BlockStateModelPart {
        private AthenaConnectedPart {
            Objects.requireNonNull(delegate, "delegate");
            quads = Collections.unmodifiableMap(
                    new LinkedHashMap<>(
                            Objects.requireNonNull(
                                    quads,
                                    "quads")));
        }

        @Override
        public List<BakedQuad> getQuads(Direction direction) {
            return quads.getOrDefault(direction, List.of());
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return delegate.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return delegate.materialFlags();
        }
    }
}
