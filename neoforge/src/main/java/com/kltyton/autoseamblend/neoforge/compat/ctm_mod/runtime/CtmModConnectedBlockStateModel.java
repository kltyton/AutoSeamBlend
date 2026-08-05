package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.pane.CtmModPanePolicy;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.CtmModOverlayStateSampler;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModGeneratedStateSprites;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModMethodStateDomain;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.runtime.geometry.IdentityPreservingListBuilder;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.render.BakedQuadTextureBasis;
import com.kltyton.autoseamblend.neoforge.runtime.render.NeoForgeQuadRetexturing;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftTopSurfaceResolver;
import com.kltyton.autoseamblend.runtime.render.DonorTintResolver;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import io.github.chiselteam.ctm.api.geometry.StandardCTMKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
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
 * 中文：由 CTM Lib 支持的动态模型，使用原生表面方向和标准连接键。 / English: CTM Lib-backed dynamic model using native face orientation and standard connection keys.
 */
final class CtmModConnectedBlockStateModel
        extends DelegateBlockStateModel {
    private static final List<Direction> CULL_FACES =
            List.of(Direction.values());
    private static final List<EngineFamily> ENGINE_FAMILIES =
            List.of(EngineFamily.values());

    private final BlockState bakedState;
    private final ConcurrentMap<SamplerKey, CtmModNativeConnectionSampler>
            samplers = new ConcurrentHashMap<>();

    CtmModConnectedBlockStateModel(
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
                && !runsCtmAutoBlend(summary)) {
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
        Map<NativeSampleKey, StandardCTMKey> nativeSamples =
                new HashMap<>();
        Map<OverlaySampleKey, NeighborConnections> overlaySamples =
                new HashMap<>();
        for (BlockStateModelPart part : base) {
            output.add(transformPart(
                    part,
                    generation,
                    level,
                    pos,
                    state,
                    seed,
                    rules,
                    surfaces,
                    summary,
                    exactSelection,
                    nativeSamples,
                    overlaySamples));
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
            MinecraftSurfaceCatalog.Snapshot surfaces,
            Optional<EngineQuerySelection> summary,
            boolean exactSelection,
            Map<NativeSampleKey, StandardCTMKey>
                    nativeSamples,
            Map<OverlaySampleKey, NeighborConnections>
                    overlaySamples) {
        LinkedHashMap<Direction, List<BakedQuad>> quads = null;
        for (Direction cullFace : CULL_FACES) {
            List<BakedQuad> source = part.getQuads(cullFace);
            List<BakedQuad> transformed = transformQuads(
                    source,
                    cullFace,
                    generation,
                    level,
                    pos,
                    state,
                    seed,
                    rules,
                    surfaces,
                    summary,
                    exactSelection,
                    nativeSamples,
                    overlaySamples);
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
                null,
                generation,
                level,
                pos,
                state,
                seed,
                rules,
                surfaces,
                summary,
                exactSelection,
                nativeSamples,
                overlaySamples);
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
                : new ConnectedPart(part, quads);
    }

    private List<BakedQuad> transformQuads(
            List<BakedQuad> source,
            Direction cullFace,
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            long seed,
            RuleRuntime.Snapshot rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            Optional<EngineQuerySelection> summary,
            boolean exactSelection,
            Map<NativeSampleKey, StandardCTMKey>
                    nativeSamples,
            Map<OverlaySampleKey, NeighborConnections>
                    overlaySamples) {
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
                        != EngineFamily.CTM_MOD
                        || !selection.orElseThrow()
                        .runsAutoBlend()) {
                    output.add(quad);
                    continue;
                }
                Optional<FaceSurface> face =
                        surfaces.face(
                                        state,
                                        quad.direction(),
                                        sprite)
                                .or(() -> surfaces
                                        .preferredFace(
                                                state,
                                                quad.direction()));
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
                ConnectionMethod method = selection.orElseThrow()
                        .resolveMethod(
                                state,
                                inferenceSurface.direction(),
                                inferenceSurface.sprite()
                                        .contents()
                                        .name());
                if (method == ConnectionMethod.TOP) {
                    output.add(
                            MinecraftTopSurfaceResolver
                                    .resolve(
                                            level,
                                            pos,
                                            state,
                                            quad.direction(),
                                            rules.rules(),
                                            surfaces)
                                    .map(topSprite ->
                                            NeoForgeQuadRetexturing.replace(
                                                    quad,
                                                    topSprite))
                                    .orElse(quad));
                    continue;
                }
                if (CtmModMethodStateDomain.replacementMethod(method)) {
                    if (CtmModPanePolicy.preservesTerminator(
                            state,
                            method,
                            quad.direction(),
                            cullFace)) {
                        // 中文：Continuity 的原版玻璃板规则只匹配 pane 主体精灵；剔除桶中的 pane_top 终止面保持原材质。
                        // English: Continuity's vanilla pane rule matches only the pane body sprite; pane_top terminators in cull buckets retain their source material.
                        output.add(quad);
                        continue;
                    }
                    TextureBasis basis =
                            BakedQuadTextureBasis.resolve(quad);
                    replacement(
                            quad,
                            surface.sprite(),
                            basis,
                            generation,
                            level,
                            pos,
                            state,
                            seed,
                            method,
                            rules,
                            nativeSamples)
                            .ifPresentOrElse(
                                    output::add,
                                    () -> output.add(quad));
                    continue;
                }
                output.add(quad);
                if (!method.overlayCapable()
                        || !surface.fullFace()
                        || !surface.facts().alphaOpaque().isTrue()) {
                    continue;
                }
                TextureBasis basis =
                        BakedQuadTextureBasis.resolve(quad);
                for (Donor donor : selectDonors(
                        level,
                        pos,
                        quad.direction(),
                        state,
                        rules.rules(),
                        surfaces)) {
                    output.addAll(overlay(
                            quad,
                            state,
                            donor,
                            basis,
                            generation,
                            level,
                            pos,
                            seed,
                            rules,
                            surfaces,
                            nativeSamples,
                            overlaySamples));
                }
            } finally {
                output.endSource();
            }
        }
        return output.finish();
    }

    private Optional<BakedQuad> replacement(
            BakedQuad quad,
            TextureAtlasSprite source,
            TextureBasis basis,
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            long seed,
            ConnectionMethod method,
            RuleRuntime.Snapshot rules,
            Map<NativeSampleKey, StandardCTMKey>
                    nativeSamples) {
        return CtmModGeneratedStateSprites
                .sprites(
                        generation,
                        source,
                        method)
                .flatMap(sprites ->
                        selectedSprite(
                                sprites,
                                source,
                                method,
                                basis,
                                level,
                                pos,
                                state,
                                quad.direction(),
                                seed,
                                rules,
                                false,
                                nativeSamples))
                .map(sprite ->
                        NeoForgeQuadRetexturing.replace(
                                quad,
                                sprite));
    }

    private List<BakedQuad> overlay(
            BakedQuad receiver,
            BlockState receiverState,
            Donor donor,
            TextureBasis basis,
            ReloadPublication.Generation generation,
            BlockAndTintGetter level,
            BlockPos pos,
            long seed,
            RuleRuntime.Snapshot rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            Map<NativeSampleKey, StandardCTMKey>
                    nativeSamples,
            Map<OverlaySampleKey, NeighborConnections>
                    overlaySamples) {
        Optional<TextureAtlasSprite[]> generated =
                CtmModGeneratedStateSprites
                        .sprites(
                                generation,
                                donor.surface().sprite(),
                                donor.method(),
                                donor.surface()
                                        .overlayProfile());
        if (generated.isEmpty()) {
            return List.of();
        }
        TextureAtlasSprite[] sprites = generated.orElseThrow();
        int tint = DonorTintResolver.resolve(
                donor.state(),
                level,
                pos,
                donor.surface().tintIndex());
        if (donor.method() == ConnectionMethod.RUNTIME_BLEND
                || donor.method() == ConnectionMethod.OVERLAY) {
            OverlaySampleKey sampleKey = new OverlaySampleKey(
                    donor,
                    receiver.direction());
            NeighborConnections connections = overlaySamples
                    .computeIfAbsent(
                            sampleKey,
                            ignored -> CtmModOverlayStateSampler.sample(
                                    level,
                                    pos,
                                    receiverState,
                                    receiver.direction(),
                                    donor,
                                    rules.rules(),
                                    surfaces));
            ArrayList<BakedQuad> output = new ArrayList<>();
            for (int slot : CtmModMethodStateDomain.selectedSlots(
                    donor.method(),
                    connections)) {
                if (slot >= 0
                        && slot < sprites.length
                        && sprites[slot] != null) {
                    output.add(NeoForgeQuadRetexturing.overlay(
                            receiver.direction(),
                            sprites[slot],
                            tint));
                }
            }
            return List.copyOf(output);
        }
        return selectedSprite(
                sprites,
                donor.surface().sprite(),
                donor.method(),
                basis,
                level,
                pos,
                donor.state(),
                receiver.direction(),
                seed,
                rules,
                true,
                nativeSamples)
                .map(sprite ->
                        NeoForgeQuadRetexturing.overlay(
                                receiver.direction(),
                                sprite,
                                tint))
                .map(List::of)
                .orElseGet(List::of);
    }

    private Optional<TextureAtlasSprite> selectedSprite(
            TextureAtlasSprite[] sprites,
            TextureAtlasSprite source,
            ConnectionMethod method,
            TextureBasis basis,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            long seed,
            RuleRuntime.Snapshot rules,
            boolean overlay,
            Map<NativeSampleKey, StandardCTMKey>
                    nativeSamples) {
        SamplerKey key = new SamplerKey(
                rules,
                state.getBlock(),
                overlay);
        CtmModNativeConnectionSampler sampler =
                samplers.computeIfAbsent(
                        key,
                        ignored ->
                                new CtmModNativeConnectionSampler(
                                        source,
                                        state.getBlock(),
                                        rules.rules(),
                                        overlay,
                                        (appearanceLevel,
                                                appearancePos,
                                                appearanceFace,
                                                appearanceState,
                                                otherState,
                                                otherPos) ->
                                                appearanceState
                                                        .getAppearance(
                                                                appearanceLevel,
                                                                appearancePos,
                                                                appearanceFace,
                                                                otherState,
                                                                otherPos)));
        NativeSampleKey sampleKey = new NativeSampleKey(
                sampler,
                state);
        NeighborConnections connections;
        if (CtmModMethodStateDomain.preservesIndependentCorners(
                method)) {
            connections = sampler.sampleIndependent(
                    level,
                    pos,
                    state,
                    face,
                    basis);
        } else {
            StandardCTMKey nativeKey = nativeSamples
                    .computeIfAbsent(
                            sampleKey,
                            ignored -> sampler.sampleKey(
                                    level,
                                    pos,
                                    state,
                                    RandomSource.create(seed)));
            connections = sampler.sample(
                    nativeKey,
                    face,
                    basis);
        }
        int slot = CtmModMethodStateDomain.stateIndex(
                method,
                connections);
        if (slot < 0
                || slot >= sprites.length
                || sprites[slot] == null) {
            return Optional.empty();
        }
        return Optional.of(sprites[slot]);
    }

    private static boolean runsCtmAutoBlend(
            Optional<EngineQuerySelection> selection) {
        return selection
                .filter(value -> value.family()
                        == EngineFamily.CTM_MOD)
                .filter(EngineQuerySelection
                        ::runsAutoBlend)
                .isPresent();
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
                EngineFamily.CTM_MOD,
                CtmModOverlayStateSampler.planarDirections(face));
    }

    private record SamplerKey(
            RuleRuntime.Snapshot ruleSnapshot,
            Block target,
            boolean overlay) {
    }

    /**
     * 中文：键只活在一次 collectParts 调用中；位置、世界和随机种子由该调用天然固定。
     * <p>
     * English:
     * This key only lives for one collectParts call, whose level, position,
     * and random seed are already fixed.
     */
    private record NativeSampleKey(
            CtmModNativeConnectionSampler sampler,
            BlockState state) {
    }

    /**
     * 中文：键只活在一次 collectParts 调用中；接收状态、世界、位置与规则快照均由该调用固定。
     * <p>
     * English:
     * This key only lives for one collectParts call, whose receiver state, level, position, and
     * rule snapshot are already fixed.
     */
    private record OverlaySampleKey(
            Donor donor,
            Direction face) {
    }

    private record ConnectedPart(
            BlockStateModelPart delegate,
            Map<Direction, List<BakedQuad>> quads)
            implements BlockStateModelPart {
        private ConnectedPart {
            Objects.requireNonNull(delegate, "delegate");
            quads = Collections.unmodifiableMap(
                    new LinkedHashMap<>(
                            Objects.requireNonNull(quads, "quads")));
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
