package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.compat.ctm_mod.runtime.CtmModOverlayStateSampler;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.pane.CtmModPanePolicy;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModMethodStateDomain;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.neoforge.runtime.render.NeoForgeQuadRetexturing;
import com.kltyton.autoseamblend.runtime.geometry.IdentityPreservingListBuilder;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.render.BakedQuadTextureBasis;
import com.kltyton.autoseamblend.runtime.render.DonorTintResolver;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftTopSurfaceResolver;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * 中文：由 CTM Lib 支持的动态模型，使用原生表面方向和标准连接键。
 *
 * English: CTM Lib-backed dynamic model using native face orientation and
 * standard connection keys.
 */
final class CtmModConnectedBlockStateModel
        extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<QueryContext> QUERY_CONTEXT =
            new ModelProperty<>();
    private static final List<Direction> CULL_FACES =
            List.of(Direction.values());
    private static final List<EngineFamily> ENGINE_FAMILIES =
            List.of(EngineFamily.values());

    private final BlockState bakedState;
    private final ConcurrentMap<SamplerKey, CtmModNativeConnectionSampler>
            samplers = new ConcurrentHashMap<>();

    CtmModConnectedBlockStateModel(
            BakedModel delegate,
            BlockState bakedState) {
        super(Objects.requireNonNull(delegate, "delegate"));
        this.bakedState =
                Objects.requireNonNull(bakedState, "bakedState");
    }

    /**
     * 中文：NeoForge 21.1 通过 ModelData 传递每方块渲染上下文。
     *
     * English: NeoForge 21.1 delivers the per-block render context through ModelData.
     */
    @Override
    public ModelData getModelData(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ModelData existing) {
        // 中文：先委托 delegate.getModelData（保留其写入的 CTM_CONTEXT 等属性），
        // 再派生叠加本包装器的 QUERY_CONTEXT。
        // English: First delegate to the original model's getModelData (preserving
        // properties such as CTM_CONTEXT it writes), then derive and add this
        // wrapper's QUERY_CONTEXT.
        return originalModel.getModelData(
                        level,
                        pos,
                        state,
                        existing)
                .derive()
                .with(
                        QUERY_CONTEXT,
                        new QueryContext(
                                level,
                                pos))
                .build();
    }

    /**
     * 中文：仅在当前代次确认该 state 可能由 CTM AutoBlend 发射 overlay 时，把
     * RenderType.cutout() 并入广告类型；否则原样返回 delegate 的类型，不给全部方块
     * 无条件增加 cutout pass。
     *
     * <p>English: Unions RenderType.cutout() into the advertised types only when the
     * current generation confirms this state may emit a CTM AutoBlend overlay;
     * otherwise returns the delegate types unchanged and never adds a cutout pass for
     * every block.
     */
    @Override
    public ChunkRenderTypeSet getRenderTypes(
            BlockState state,
            RandomSource random,
            ModelData modelData) {
        ChunkRenderTypeSet delegateTypes =
                originalModel.getRenderTypes(
                        state,
                        random,
                        modelData);
        boolean needsOverlay = ReloadPublication.read(generation ->
                needsOverlay(
                        generation,
                        state,
                        EngineQueryRouter.select(
                                state,
                                generation)));
        if (!needsOverlay) {
            return delegateTypes;
        }
        return ChunkRenderTypeSet.of(
                CtmModRenderPassPolicy.advertisedTypes(
                        delegateTypes,
                        true));
    }

    @Override
    public List<BakedQuad> getQuads(
            BlockState state,
            Direction direction,
            RandomSource random,
            ModelData modelData,
            RenderType renderType) {
        QueryContext context = modelData == null
                ? null
                : modelData.get(QUERY_CONTEXT);
        if (state != bakedState
                || context == null) {
            return originalModel.getQuads(
                    state,
                    direction,
                    random,
                    modelData,
                    renderType);
        }
        return ReloadPublication.read(generation ->
                getQuads(
                        generation,
                        state,
                        direction,
                        random,
                        modelData,
                        renderType,
                        context));
    }

    private List<BakedQuad> getQuads(
            ReloadPublication.Generation generation,
            BlockState state,
            Direction direction,
            RandomSource random,
            ModelData modelData,
            RenderType renderType,
            QueryContext context) {
        boolean exactSelection = requiresExactSelection(
                generation,
                state);
        Optional<EngineQuerySelection> summary =
                EngineQueryRouter.select(
                        state,
                        generation);
        if (!exactSelection
                && !runsCtmAutoBlend(summary)) {
            return originalModel.getQuads(
                    state,
                    direction,
                    random,
                    modelData,
                    renderType);
        }
        long seed = random.nextLong();
        RuleRuntime.Snapshot rules =
                generation.selectors();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                generation.surfaces();
        boolean needsOverlay =
                needsOverlay(
                        generation,
                        state,
                        summary);
        ChunkRenderTypeSet delegateTypes =
                originalModel.getRenderTypes(
                        state,
                        RandomSource.create(seed),
                        modelData);
        CtmModRenderPassPolicy.PassDecision decision =
                CtmModRenderPassPolicy.decision(
                        delegateTypes,
                        renderType,
                        needsOverlay);
        boolean basePass = decision.basePass();
        boolean overlayPass = decision.overlayPass();
        List<BakedQuad> source;
        if (basePass) {
            source = originalModel.getQuads(
                    state,
                    direction,
                    RandomSource.create(seed),
                    modelData,
                    renderType);
        } else if (overlayPass) {
            source = overlaySourceQuads(
                    generation,
                    state,
                    direction,
                    delegateTypes,
                    seed,
                    modelData);
        } else {
            source = List.of();
        }
        Map<NativeSampleKey, NeighborConnections> nativeSamples =
                new HashMap<>();
        Map<OverlaySampleKey, NeighborConnections> overlaySamples =
                new HashMap<>();
        return transformQuads(
                source,
                direction,
                generation,
                context.level(),
                context.pos(),
                state,
                seed,
                rules,
                surfaces,
                summary,
                exactSelection,
                basePass,
                overlayPass,
                nativeSamples,
                overlaySamples);
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
            boolean includeBase,
            boolean includeOverlay,
            Map<NativeSampleKey, NeighborConnections>
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
                        quad.getSprite();
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
                    if (includeBase) {
                        output.add(quad);
                    }
                    continue;
                }
                Optional<FaceSurface> face =
                        surfaces.face(
                                        state,
                                        quad.getDirection(),
                                        sprite)
                                .or(() -> surfaces
                                        .preferredFace(
                                                state,
                                                quad.getDirection()));
                if (face.isEmpty()) {
                    if (includeBase) {
                        output.add(quad);
                    }
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
                    if (includeBase) {
                        output.add(
                                MinecraftTopSurfaceResolver
                                        .resolve(
                                                level,
                                                pos,
                                                state,
                                                quad.getDirection(),
                                                rules.rules(),
                                                surfaces)
                                        .map(topSprite ->
                                                NeoForgeQuadRetexturing.replace(
                                                        quad,
                                                        topSprite))
                                        .orElse(quad));
                    }
                    continue;
                }
                if (CtmModMethodStateDomain.replacementMethod(method)) {
                    if (CtmModPanePolicy.preservesTerminator(
                            state,
                            method,
                            quad.getDirection(),
                            cullFace)) {
                        // 中文：Continuity 的原版玻璃板规则只匹配 pane 主体精灵；剔除桶中的 pane_top 终止面保持原材质。
                        // English: Continuity's vanilla pane rule matches only the pane body sprite; pane_top terminators in cull buckets retain their source material.
                        if (includeBase) {
                            output.add(quad);
                        }
                        continue;
                    }
                    if (includeBase) {
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
                    }
                    continue;
                }
                if (includeBase) {
                    output.add(quad);
                }
                if (includeOverlay
                        && quad.getDirection() != null) {
                    if (method.overlayCapable()
                            && surface.fullFace()
                            && surface.facts()
                                    .alphaOpaque()
                                    .isTrue()) {
                        TextureBasis basis =
                                BakedQuadTextureBasis.resolve(quad);
                        for (Donor donor : selectDonors(
                                level,
                                pos,
                                quad.getDirection(),
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
                }
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
            Map<NativeSampleKey, NeighborConnections>
                    nativeSamples) {
        Optional<TextureAtlasSprite[]> stateSprites =
                CtmModGeneratedStateSprites
                .sprites(
                        generation,
                        source,
                        method);
        if (stateSprites.isEmpty()) {
            return Optional.empty();
        }
        TextureAtlasSprite[] sprites = stateSprites.orElseThrow();
        Optional<TextureAtlasSprite> selected = selectedSprite(
                sprites,
                source,
                method,
                basis,
                level,
                pos,
                state,
                quad.getDirection(),
                seed,
                rules,
                false,
                nativeSamples);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        return selected.map(sprite ->
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
            Map<NativeSampleKey, NeighborConnections>
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
                    receiver.getDirection());
            NeighborConnections connections = overlaySamples
                    .computeIfAbsent(
                            sampleKey,
                            ignored -> CtmModOverlayStateSampler.sample(
                                    level,
                                    pos,
                                    receiverState,
                                    receiver.getDirection(),
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
                            receiver.getDirection(),
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
                receiver.getDirection(),
                seed,
                rules,
                true,
                nativeSamples)
                .map(sprite ->
                        NeoForgeQuadRetexturing.overlay(
                                receiver.getDirection(),
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
            Map<NativeSampleKey, NeighborConnections>
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
                                                appearanceState.getAppearance(
                                                        appearanceLevel,
                                                        appearancePos,
                                                        appearanceFace,
                                                        otherState,
                                                        otherPos)));
        NativeSampleKey sampleKey = new NativeSampleKey(
                sampler,
                state,
                face);
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
            connections = nativeSamples
                    .computeIfAbsent(
                            sampleKey,
                            ignored -> sampler.sampleStandard(
                                    level,
                                    pos,
                                    state,
                                    face,
                                    basis,
                                    RandomSource.create(seed)));
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

    /**
     * 中文：当前代次下该 state 是否可能由 CTM AutoBlend 发射 overlay：summary 必须是
     * CTM_MOD 且 runsAutoBlend；selection.method 具体时看 overlayCapable，AUTO 时看
     * 该 state 所有 FaceSurface.inferredMethod 是否任一 overlayCapable。
     *
     * <p>English: Whether the current generation can emit a CTM AutoBlend overlay for
     * this state: the summary must be CTM_MOD with runsAutoBlend; a concrete
     * selection.method must be overlayCapable, and for AUTO any FaceSurface
     * inferredMethod must be overlayCapable.
     */
    private static boolean needsOverlay(
            ReloadPublication.Generation generation,
            BlockState state,
            Optional<EngineQuerySelection> summary) {
        if (summary.isEmpty()) {
            return false;
        }
        EngineQuerySelection selection = summary.orElseThrow();
        if (selection.family() != EngineFamily.CTM_MOD
                || !selection.runsAutoBlend()) {
            return false;
        }
        ConnectionMethod method = selection.method();
        if (method != ConnectionMethod.AUTO) {
            return method.overlayCapable();
        }
        MinecraftSurfaceCatalog.StateSurface stateSurface =
                generation.surfaces().states().get(state);
        if (stateSurface == null) {
            return false;
        }
        for (List<FaceSurface> faces : stateSurface.faces().values()) {
            for (FaceSurface face : faces) {
                if (face.inferredMethod().overlayCapable()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 中文：额外 cutout pass 的只读计算输入。direction 非 null 时取该方向所有
     * FaceSurface.representativeQuad；direction 为 null 时遍历 delegate 原始 render
     * types 的 null 桶 5 参 quad 并按对象身份去重。该输入绝不作为输出返回。
     *
     * <p>English: Read-only computation input for the extra cutout pass. For a concrete
     * direction, collects all FaceSurface.representativeQuad of that direction; for null,
     * collects the delegate's null-bucket 5-arg quads per original render type, deduped
     * by object identity. This input is never returned as output.
     */
    private List<BakedQuad> overlaySourceQuads(
            ReloadPublication.Generation generation,
            BlockState state,
            Direction direction,
            Iterable<RenderType> delegateTypes,
            long seed,
            ModelData modelData) {
        if (direction == null) {
            IdentityHashMap<BakedQuad, Boolean> seen =
                    new IdentityHashMap<>();
            ArrayList<BakedQuad> quads = new ArrayList<>();
            for (RenderType type : delegateTypes) {
                for (BakedQuad quad : originalModel.getQuads(
                        state,
                        null,
                        RandomSource.create(seed),
                        modelData,
                        type)) {
                    if (seen.put(quad, Boolean.TRUE) == null) {
                        quads.add(quad);
                    }
                }
            }
            return List.copyOf(quads);
        }
        MinecraftSurfaceCatalog.StateSurface stateSurface =
                generation.surfaces().states().get(state);
        if (stateSurface == null) {
            return List.of();
        }
        ArrayList<BakedQuad> quads = new ArrayList<>();
        for (FaceSurface face : stateSurface.faces()
                .getOrDefault(direction, List.of())) {
            quads.add(face.representativeQuad());
        }
        return List.copyOf(quads);
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

    /**
     * 中文：由 getModelData 捕获的渲染上下文。
     *
     * English: Render context captured by getModelData.
     */
    private record QueryContext(
            BlockAndTintGetter level,
            BlockPos pos) {
        private QueryContext {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(pos, "pos");
        }
    }

    private record SamplerKey(
            RuleRuntime.Snapshot ruleSnapshot,
            Block target,
            boolean overlay) {
    }

    private record NativeSampleKey(
            CtmModNativeConnectionSampler sampler,
            BlockState state,
            Direction face) {
    }

    private record OverlaySampleKey(
            Donor donor,
            Direction face) {
    }
}
