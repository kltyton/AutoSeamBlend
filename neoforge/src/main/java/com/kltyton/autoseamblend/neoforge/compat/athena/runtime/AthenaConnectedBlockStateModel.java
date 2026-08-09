package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.plan.AthenaMethodPolicy;
import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.runtime.geometry.IdentityPreservingListBuilder;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
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
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * 中文：由 Athena 支持的动态模型，每次运行时查询都使用 Athena 的外观与 CTM 状态。
 *
 * <p>English:
 * Athena-backed dynamic model using Athena's appearance and CTM state for every runtime query.
 */
final class AthenaConnectedBlockStateModel
        extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<QueryContext> QUERY_CONTEXT =
            new ModelProperty<>();
    private static final List<Direction> CULL_FACES =
            List.of(Direction.values());
    private static final List<EngineFamily> ENGINE_FAMILIES =
            List.of(EngineFamily.values());

    private final BlockState bakedState;

    AthenaConnectedBlockStateModel(
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
        // 中文：先委托 delegate.getModelData（保留其写入的属性，例如 Athena 或其他
        // 动态模型的每方块数据），再派生叠加本包装器的 QUERY_CONTEXT。
        // English: First delegate to the original model's getModelData (preserving any
        // per-block property it writes), then derive and add this wrapper's QUERY_CONTEXT.
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

    /**
     * 中文：仅在当前代次确认该 state 可能由 Athena AutoBlend 发射 overlay 时，把
     * RenderType.cutout() 并入广告类型；否则原样返回 delegate 的类型，不给全部方块
     * 无条件增加 cutout pass。
     *
     * <p>English: Unions RenderType.cutout() into the advertised types only when the
     * current generation confirms this state may emit an Athena AutoBlend overlay;
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
                AthenaRenderPassPolicy.advertisedTypes(
                        delegateTypes,
                        true));
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
                && !AthenaMethodPolicy.runsAthenaAutoBlend(summary)) {
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
        AthenaRenderPassPolicy.PassDecision decision =
                AthenaRenderPassPolicy.decision(
                        delegateTypes,
                        renderType,
                        needsOverlay);
        List<BakedQuad> source;
        if (decision.basePass()) {
            // 中文：base pass 用 5 参取 delegate quad，保留 ModelData/RenderType。
            // English: The base pass fetches delegate quads through the 5-arg overload,
            // preserving ModelData/RenderType.
            source = originalModel.getQuads(
                    state,
                    direction,
                    RandomSource.create(seed),
                    modelData,
                    renderType);
        } else if (decision.overlayPass()) {
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
        return transformQuads(
                source,
                generation,
                context.level(),
                context.pos(),
                state,
                rules.rules(),
                surfaces,
                summary,
                exactSelection,
                decision.basePass(),
                decision.overlayPass());
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
            boolean exactSelection,
            boolean includeBase,
            boolean includeOverlay) {
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
                        != EngineFamily.ATHENA
                        || !selection.orElseThrow()
                        .runsAutoBlend()) {
                    if (includeBase) {
                        output.add(quad);
                    }
                    continue;
                }
                EngineQuerySelection selected =
                        selection.orElseThrow();
                Optional<FaceSurface> face =
                        surfaces.face(
                                state,
                                quad.getDirection(),
                                sprite);
                if (face.isEmpty()) {
                    // 中文：Athena 的每个 Quad 必须只绑定它自己的原始精灵；玻璃板窄边、盖板或其他次级材质不能借用同方向的主体面。
                    // English: Every Athena quad must bind only its own source sprite; pane strips, caps, and other secondary materials must not borrow the preferred surface on that face.
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
                                    quad.getDirection(),
                                    rules,
                                    surfaces);
                    BakedQuad topResult = topSprite
                            .map(value -> AthenaNativeQuadProcessor.retexture(quad, value))
                            .orElse(quad);
                    if (includeBase) {
                        output.add(topResult);
                    }
                    continue;
                }
                if (AthenaMethodPolicy.replacement(method)) {
                    if (!includeBase) {
                        continue;
                    }
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
                if (includeBase) {
                    output.add(quad);
                }
                // 中文：null 桶 quad 无 cull face，overlay 需要确定面进行供体选择与
                // 偏移；与 CTM 已验收实现一致，在 overlay pass 跳过。
                // English: Null-bucket quads have no cull face; overlays need a concrete
                // face for donor selection and offsetting, matching the accepted CTM
                // implementation, so they are skipped on the overlay pass.
                if (!includeOverlay
                        || quad.getDirection() == null
                        || !AthenaMethodPolicy.overlay(method)
                        || !surface.fullFace()
                        || !surface.facts().alphaOpaque().isTrue()) {
                    continue;
                }
                BlockState outward = level.getBlockState(
                        pos.relative(quad.getDirection()));
                if (outward.isSolidRender(
                        level,
                        pos.relative(quad.getDirection()))) {
                    // 中文：完整实心邻块已遮住接收面；在供体选择和两轮 8 邻域采样前退出，避免不可见地下表面的无效工作。
                    // English: A fully solid neighbor already occludes the receiver face; exit before donor selection and both eight-neighbor sampling passes.
                    continue;
                }
                List<Donor> donors = selectDonors(
                        level,
                        pos,
                        quad.getDirection(),
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

    /**
     * 中文：当前代次下该 state 是否可能由 Athena AutoBlend 发射 overlay：summary 必须是
     * ATHENA 且 runsAutoBlend；selection.method 具体时看 overlayCapable，AUTO 时看
     * 该 state 所有 FaceSurface.inferredMethod 是否任一 overlayCapable。
     *
     * <p>English: Whether the current generation can emit an Athena AutoBlend overlay for
     * this state: the summary must be ATHENA with runsAutoBlend; a concrete
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
        if (selection.family() != EngineFamily.ATHENA
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
}
