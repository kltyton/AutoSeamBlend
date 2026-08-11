package com.kltyton.autoseamblend.forge.compat.continuity.runtime;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityProcessorHolderFactory;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityMethodPolicy;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityPredicateBinding;
import com.kltyton.autoseamblend.compat.continuity.runtime.overlay.ContinuityOverlayOrchestrator;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftTopSurfaceResolver;
import com.kltyton.autoseamblend.compat.continuity.runtime.state.ContinuityNativeStateProcessors;
import com.kltyton.autoseamblend.authoring.preview.PreviewQuery;
import com.kltyton.autoseamblend.authoring.preview.PreviewSample;
import com.kltyton.autoseamblend.compat.continuity.runtime.texture.ContinuityGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.routing.query.EngineRouteProvenance;
import com.kltyton.autoseamblend.runtime.overlay.OverlayCandidatePriority;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.model.QuadProcessors;
import me.pepperbell.continuity.client.processor.ConnectionPredicate;
import me.pepperbell.continuity.client.processor.DirectionMaps;
import me.pepperbell.continuity.client.processor.ProcessingPredicate;
import me.pepperbell.continuity.client.processor.simple.SimpleQuadProcessor;
import me.pepperbell.continuity.client.util.QuadUtil;
import me.pepperbell.continuity.client.util.RenderUtil;
import net.minecraft.world.level.BlockAndTintGetter;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：用于隐式和配置 AutoBlend 表面的 NeoContinuity 运行时实现。 / English: NeoContinuity-backed runtime implementation for implicit and configured AutoBlend surfaces. */
public final class ContinuityAutoBlendProcessor implements QuadProcessor {
    private static final ContinuityAutoBlendProcessor INSTANCE =
            new ContinuityAutoBlendProcessor();
    private static final ProcessingPredicate ACCEPTED_QUERY =
            (quad, sprite, level, appearanceState, state, pos, data) -> true;
    private static final ConcurrentMap<OverlaySelectorKey, NativeOverlaySelector>
            OVERLAY_SELECTORS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ReplacementProcessorKey, QuadProcessor>
            REPLACEMENT_PROCESSORS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<OverlayCtmProcessorKey, QuadProcessor>
            OVERLAY_CTM_PROCESSORS = new ConcurrentHashMap<>();
    private static volatile ReloadPublication.Generation
            cachedPublication;

    private ContinuityAutoBlendProcessor() {}

    public static QuadProcessors.ProcessorHolder holder() {
        return ContinuityProcessorHolderFactory.create(INSTANCE);
    }

    @Override
    public ProcessingResult processQuad(
            MutableQuadView quad,
            TextureAtlasSprite sprite,
            BlockAndTintGetter level,
            BlockState appearanceState,
            BlockState state,
            BlockPos pos,
            Supplier<RandomSource> randomSupplier,
            int pass,
            ProcessingContext context) {
        return ReloadPublication.read(generation ->
                processQuad(
                        generation,
                        quad,
                        sprite,
                        level,
                        appearanceState,
                        state,
                        pos,
                        randomSupplier,
                        pass,
                        context));
    }

    private ProcessingResult processQuad(
            ReloadPublication.Generation generation,
            MutableQuadView quad,
            TextureAtlasSprite sprite,
            BlockAndTintGetter level,
            BlockState appearanceState,
            BlockState state,
            BlockPos pos,
            Supplier<RandomSource> randomSupplier,
            int pass,
            ProcessingContext context) {
        if (pass != 0) {
            return ProcessingResult.NEXT_PROCESSOR;
        }
        boolean engineSelected =
                EngineQueryRouter
                        .select(
                                state,
                                NativeOwnershipTracker
                                        .nativeAuthorExact(),
                                generation)
                        .map(value ->
                                value.family()
                                        == EngineFamily.MCPATCHER)
                        .orElse(false);
        if (!engineSelected) {
            return ProcessingResult.NEXT_PROCESSOR;
        }
        RuleRuntime.Snapshot ruleSnapshot =
                generation.selectors();
        if (!NativeOwnershipTracker.allowsAutoBlend(
                ruleSnapshot,
                state.getBlock())) {
            return ProcessingResult.NEXT_PROCESSOR;
        }
        MinecraftSurfaceCatalog.Snapshot surfaces =
                generation.surfaces();
        refreshSelectorCache(generation);
        Optional<FaceSurface> currentSurface =
                surfaces.face(
                        state,
                        quad.lightFace(),
                        quad.nominalFace(),
                        sprite);
        if (currentSurface.isEmpty()) {
            return ProcessingResult.NEXT_PROCESSOR;
        }

        ConnectionMethod currentMethod = method(
                state,
                currentSurface.orElseThrow(),
                ruleSnapshot.rules(),
                surfaces);
        ContinuityMethodPolicy.RuntimeAction action =
                ContinuityMethodPolicy.action(currentMethod);
        if (action == ContinuityMethodPolicy.RuntimeAction.TOP) {
            return replaceTopSurface(
                    quad,
                    sprite,
                    level,
                    pos,
                    state,
                    ruleSnapshot.rules(),
                    surfaces);
        }
        if (action == ContinuityMethodPolicy.RuntimeAction.REPLACEMENT) {
            return replaceConnectedSurface(
                    quad,
                    sprite,
                    level,
                    appearanceState,
                    state,
                    pos,
                    currentMethod,
                    ruleSnapshot.rules(),
                    ruleSnapshot.generation(),
                    surfaces.generation(),
                    generation,
                    randomSupplier,
                    context);
        }
        if (action != ContinuityMethodPolicy.RuntimeAction.OVERLAY) {
            return ProcessingResult.NEXT_PROCESSOR;
        }

        FaceSurface receiver = currentSurface.orElseThrow();
        if (!receiver.fullFace()
                || !receiver.facts().alphaOpaque().isTrue()
                || !QuadUtil.isQuadUnitSquare(quad)) {
            return ProcessingResult.NEXT_PROCESSOR;
        }
        List<ContinuityOverlayOrchestrator.Candidate<FaceSurface>> donors =
                selectDonors(
                        level,
                        pos,
                        quad.lightFace(),
                        state,
                        ruleSnapshot.rules(),
                        surfaces,
                        generation);
        for (ContinuityOverlayOrchestrator.Candidate<FaceSurface> candidate : donors) {
            Donor selected = new Donor(
                    candidate.state(),
                    candidate.surface(),
                    candidate.method());
            Optional<TextureAtlasSprite[]> stateSprites =
                    ContinuityGeneratedStateSprites
                            .sprites(
                                    generation,
                                    selected.surface()
                                            .sprite(),
                                    selected.method(),
                                    selected.surface()
                                            .overlayProfile());
            if (stateSprites.isEmpty()) {
                continue;
            }
            if (selected.method()
                    == ConnectionMethod.OVERLAY_CTM) {
                if (!NativeOwnershipTracker
                        .nativeAuthorExact()) {
                    emitOverlayCtm(
                            quad,
                            sprite,
                            level,
                            appearanceState,
                            state,
                            pos,
                            randomSupplier,
                            pass,
                            context,
                            selected,
                            stateSprites.orElseThrow(),
                            generation,
                            ruleSnapshot);
                }
                continue;
            }
            OverlaySelectorKey key = new OverlaySelectorKey(
                    ruleSnapshot.generation(),
                    surfaces.generation(),
                    quad.lightFace(),
                    selected.state(),
                    selected.surface().sprite());
            NativeOverlaySelector selector =
                    OVERLAY_SELECTORS.computeIfAbsent(
                            key,
                            ignored -> selector(
                                    quad.lightFace(),
                                    selected,
                                    ruleSnapshot.rules(),
                                    surfaces));
            List<Integer> requestedSlots = selector.select(
                    quad,
                    sprite,
                    level,
                    appearanceState,
                    state,
                    pos,
                    context);
            List<Integer> nativeSlots =
                    NativeOwnershipTracker
                            .filterAutoBlendOverlaySlots(
                                    requestedSlots);
            if (nativeSlots.isEmpty()) {
                continue;
            }
            if (RendererAccess.INSTANCE.getRenderer()
                    == null) {
                continue;
            }
            int tint = RenderUtil.getTintColor(
                    selected.state(),
                    level,
                    pos,
                    selected.surface().tintIndex());
            TextureAtlasSprite[] generated =
                    stateSprites.orElseThrow();
            // 中文：复用锁定 NeoContinuity 公开 RenderUtil.findOverlayMaterial（javap 已证明
            // 签名），由其线程局部 MaterialFinder 设置 blendMode 与 AO；不复制引擎算法。
            // English: Reuse the locked NeoContinuity public RenderUtil.findOverlayMaterial
            // (signature javap-verified); its thread-local MaterialFinder applies blendMode
            // and AO. No engine algorithm is copied.
            RenderMaterial cutoutMaterial =
                    RenderUtil.findOverlayMaterial(
                            BlendMode.CUTOUT,
                            selected.state());
            for (int slot : nativeSlots) {
                if (slot < 0
                        || slot >= generated.length) {
                    continue;
                }
                QuadUtil.emitOverlayQuad(
                        context.getExtraQuadEmitter(),
                        quad.lightFace(),
                        generated[slot],
                        tint,
                        cutoutMaterial);
                // 中文：1.20.1 的 ProcessingContextImpl.outputTo 仅在 hasExtraQuads=true 时把
                // getExtraQuadEmitter() 的 mesh 输出到渲染；26.1.2 的 QuadCollectionBuilder 在
                // emitOverlayQuad 内部直接 emitQuad。缺失本调用会导致全部 overlay quad 被丢弃，
                // 草方块连接在世界与 GUI 预览中完全不可见。
                // English: In 1.20.1, ProcessingContextImpl.outputTo emits the extra-quad mesh
                // only when hasExtraQuads=true; 26.1.2's QuadCollectionBuilder emits inside
                // emitOverlayQuad. Without this call every overlay quad is dropped, so grass
                // blending disappears in both the world and the GUI preview.
                context.markHasExtraQuads();
            }
        }
        return ProcessingResult.NEXT_PROCESSOR;
    }

    private static ProcessingResult replaceTopSurface(
            MutableQuadView quad,
            TextureAtlasSprite sprite,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        ProcessingResult result = MinecraftTopSurfaceResolver
                .resolve(
                        level,
                        pos,
                        state,
                        quad.lightFace(),
                        rules,
                        surfaces)
                .map(topSprite ->
                        SimpleQuadProcessor.process(
                                quad,
                                sprite,
                                topSprite))
                .orElse(
                        ProcessingResult.NEXT_PROCESSOR);
        return result;
    }

    private static ProcessingResult replaceConnectedSurface(
            MutableQuadView quad,
            TextureAtlasSprite sprite,
            BlockAndTintGetter level,
            BlockState appearanceState,
            BlockState state,
            BlockPos pos,
            ConnectionMethod method,
            ConnectionRuleSet<Block> rules,
            long ruleGeneration,
            long surfaceGeneration,
            ReloadPublication.Generation generation,
            Supplier<RandomSource> randomSupplier,
            ProcessingContext context) {
        Optional<TextureAtlasSprite[]> generated =
                ContinuityGeneratedStateSprites
                        .sprites(
                                generation,
                                sprite,
                                method);
        if (generated.isEmpty()) {
            return ProcessingResult.NEXT_PROCESSOR;
        }
        ConnectionPredicate predicate =
                (world, originAppearance, originState, originPos,
                        otherAppearance, otherState, otherPos,
                        face, quadSprite) -> {
                    BlockState origin =
                            ContinuityPredicateBinding.originState(
                                    originAppearance,
                                    originState);
                    BlockState other =
                            ContinuityPredicateBinding.neighborState(
                                    otherAppearance,
                                    otherState);
                    boolean connects =
                            ContinuityMethodPolicy.connects(
                                    rules,
                                    origin.getBlock(),
                                    other.getBlock());
                    return connects;
                };
        ReplacementProcessorKey key =
                new ReplacementProcessorKey(
                        ruleGeneration,
                        surfaceGeneration,
                        state.getBlock(),
                        method,
                        sprite);
        QuadProcessor processor =
                REPLACEMENT_PROCESSORS.computeIfAbsent(
                        key,
                        ignored ->
                                ContinuityNativeStateProcessors
                                        .replacement(
                                                method,
                                                generated.orElseThrow(),
                                                predicate,
                                                ACCEPTED_QUERY));
        ProcessingResult result = processor.processQuad(
                quad,
                sprite,
                level,
                appearanceState,
                state,
                pos,
                randomSupplier,
                0,
                context);
        return result;
    }

    private static void emitOverlayCtm(
            MutableQuadView quad,
            TextureAtlasSprite sprite,
            BlockAndTintGetter level,
            BlockState appearanceState,
            BlockState state,
            BlockPos pos,
            Supplier<RandomSource> randomSupplier,
            int pass,
            ProcessingContext context,
            Donor donor,
            TextureAtlasSprite[] generated,
            ReloadPublication.Generation generation,
            RuleRuntime.Snapshot rules) {
        Block donorBlock =
                donor.state().getBlock();
        ConnectionPredicate predicate =
                (world, originAppearance, originState, originPos,
                        otherAppearance, otherState, otherPos,
                        face, quadSprite) ->
                        ContinuityMethodPolicy.connects(
                                rules.rules(),
                                donorBlock,
                                ContinuityPredicateBinding
                                        .neighborState(
                                                otherAppearance,
                                                otherState)
                                        .getBlock());
        OverlayCtmProcessorKey key =
                new OverlayCtmProcessorKey(
                        rules.generation(),
                        generation
                                .surfaces()
                                .generation(),
                        state.getBlock(),
                        donor.state(),
                        donor.surface().sprite());
        QuadProcessor processor =
                OVERLAY_CTM_PROCESSORS
                        .computeIfAbsent(
                                key,
                                ignored ->
                                        ContinuityNativeStateProcessors
                                                .overlayCtm(
                                                         generated,
                                                         predicate,
                                                         ACCEPTED_QUERY,
                                                         donor.surface()
                                                                .tintIndex(),
                                                        donor.state()));
        processor.processQuad(
                quad,
                sprite,
                level,
                appearanceState,
                state,
                pos,
                randomSupplier,
                pass,
                context);
    }

    private static List<ContinuityOverlayOrchestrator.Candidate<FaceSurface>> selectDonors(
            BlockAndTintGetter level,
            BlockPos pos,
            Direction face,
            BlockState receiver,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            ReloadPublication.Generation generation) {
        return ContinuityOverlayOrchestrator.selectDonors(
                level,
                pos,
                face,
                receiver,
                rules,
                Arrays.asList(DirectionMaps.getMap(face)[0]),
                candidate -> candidate(
                        candidate,
                        face,
                        rules,
                        surfaces,
                        generation),
                priority(
                        receiver,
                        face,
                        rules,
                        surfaces,
                        generation));
    }

    private static Optional<ContinuityOverlayOrchestrator.Candidate<FaceSurface>> candidate(
            BlockState state,
            Direction face,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            ReloadPublication.Generation generation) {
        Optional<FaceSurface> surface = surfaces.preferredFace(state, face);
        Optional<EngineQuerySelection> selection =
                EngineQueryRouter.select(state, generation)
                        .filter(value -> value.family() == EngineFamily.MCPATCHER);
        if (surface.isEmpty() || selection.isEmpty()) {
            return Optional.empty();
        }
        FaceSurface resolvedSurface = surface.orElseThrow();
        ConnectionMethod method = OverlayDonorResolution.resolveMethod(
                EngineFamily.MCPATCHER,
                state,
                resolvedSurface,
                rules,
                surfaces);
        if (!ContinuityMethodPolicy.overlay(method)) {
            return Optional.empty();
        }
        EngineRouteProvenance provenance = selection.orElseThrow()
                .route()
                .provenance();
        return Optional.of(new ContinuityOverlayOrchestrator.Candidate<>(
                state,
                resolvedSurface,
                method,
                new OverlayCandidatePriority(
                        provenance.sourceTier(),
                        provenance.packPriority(),
                        provenance.order(),
                        resolvedSurface.overlayProfile().dominance(),
                        resolvedSurface.overlayProfile().visualSignature())));
    }

    private static Optional<OverlayCandidatePriority> priority(
            BlockState state,
            Direction face,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            ReloadPublication.Generation generation) {
        return candidate(state, face, rules, surfaces, generation)
                .map(ContinuityOverlayOrchestrator.Candidate::priority);
    }

    /**
     * 中文：为工作台预览复用 Runtime 的同一个 NeoContinuity overlay 选择器。
     *
     * English:
     * Reuses Runtime's exact NeoContinuity overlay selector for the workbench
     * preview.
     */
    public static List<Integer> previewOverlaySlots(
            PreviewQuery query,
            PreviewSample sample,
            QuadProcessor.ProcessingContext context) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(context, "context");
        Donor donor = new Donor(
                sample.sourceState(),
                sample.sourceSurface(),
                sample.renderMethod());
        NativeOverlaySelector selected =
                query.usesDocumentConnectionBlocks()
                        ? previewSelector(
                                query,
                                donor)
                        : selector(
                                query.face(),
                                donor,
                                query.rules().rules(),
                                query.surfaces());
        List<Integer> slots =
                selected.select(
                        QuadConversions.fromBakedQuad(
                                query.surface()
                                        .representativeQuad()),
                        query.surface()
                                .sprite(),
                        query.level(),
                        query.state(),
                        query.state(),
                        query.pos(),
                        context);
        return NativeOwnershipTracker
                .filterAutoBlendOverlaySlots(
                        slots);
    }

    private static NativeOverlaySelector
            previewSelector(
                    PreviewQuery query,
                    Donor donor) {
        return new NativeOverlaySelector(
                donor.surface().sprite(),
                candidate -> candidate.getBlock()
                        == query.state()
                                .getBlock(),
                candidate -> query.connects(
                        query.state(),
                        candidate),
                (world, originAppearance, originState,
                        originPos, otherAppearance,
                        otherState, otherPos,
                        face, quadSprite) ->
                                query.connects(
                                        ContinuityPredicateBinding
                                                .originState(
                                                        originAppearance,
                                                        originState),
                                        ContinuityPredicateBinding
                                                .neighborState(
                                                        otherAppearance,
                                                        otherState)),
                donor.surface().tintIndex(),
                donor.state());
    }

    private static NativeOverlaySelector selector(
            Direction queryFace,
            Donor donor,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        Block donorBlock = donor.state().getBlock();
        ConcurrentMap<BlockState, Boolean> receivers =
                new ConcurrentHashMap<>();
        return new NativeOverlaySelector(
                donor.surface().sprite(),
                candidate -> receivers.computeIfAbsent(
                        candidate,
                        state -> OverlayDonorResolution.receivesOverlayFrom(
                                EngineFamily.MCPATCHER,
                                donor,
                                state,
                                queryFace,
                                rules,
                                surfaces)),
                candidate -> ContinuityMethodPolicy.connects(
                        rules,
                        donorBlock,
                        candidate.getBlock()),
                (world, originAppearance, originState, originPos,
                        otherAppearance, otherState, otherPos,
                        face, quadSprite) ->
                        ContinuityMethodPolicy.connects(
                                rules,
                                ContinuityPredicateBinding
                                        .originState(
                                                originAppearance,
                                                originState)
                                        .getBlock(),
                                ContinuityPredicateBinding
                                        .neighborState(
                                                otherAppearance,
                                                otherState)
                                        .getBlock()),
                donor.surface().tintIndex(),
                donor.state());
    }

    private static ConnectionMethod method(
            BlockState state,
            FaceSurface surface,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        Block block = state.getBlock();
        Optional<ConnectionMethod> owned =
                NativeOwnershipTracker
                        .effectiveMethod();
        ConnectionMethod configured = owned
                .orElseGet(() -> rules.isTarget(block)
                        ? rules.method(block)
                        : ConnectionMethod.AUTO);
        ConnectionMethod resolved =
                configured == ConnectionMethod.AUTO
                        ? OverlayDonorResolution.resolveMethod(
                                EngineFamily.MCPATCHER,
                                state,
                                surface,
                                rules,
                                surfaces)
                        : configured;
        if (owned.isPresent()) {
            return resolved;
        }
        boolean excluded = !rules.excludedModes(block, resolved).isEmpty();
        return excluded ? ConnectionMethod.NONE : resolved;
    }

    private static void refreshSelectorCache(
            ReloadPublication.Generation generation) {
        if (cachedPublication == generation) {
            return;
        }
        synchronized (OVERLAY_SELECTORS) {
            if (cachedPublication != generation) {
                OVERLAY_SELECTORS.clear();
                REPLACEMENT_PROCESSORS.clear();
                OVERLAY_CTM_PROCESSORS.clear();
                cachedPublication = generation;
            }
        }
    }

    private record OverlaySelectorKey(
            long ruleGeneration,
            long surfaceGeneration,
            Direction face,
            BlockState donorState,
            TextureAtlasSprite donorSprite) {}

    private record ReplacementProcessorKey(
            long ruleGeneration,
            long surfaceGeneration,
            Block block,
            ConnectionMethod method,
            TextureAtlasSprite sourceSprite) {}

    private record OverlayCtmProcessorKey(
            long ruleGeneration,
            long surfaceGeneration,
            Block receiver,
            BlockState donorState,
            TextureAtlasSprite donorSprite) {}
}
