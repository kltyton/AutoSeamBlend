package com.kltyton.autoseamblend.forge.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionNativeQuadProcessor;
import com.kltyton.autoseamblend.compat.fusion.runtime.texture.FusionGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.runtime.geometry.IdentityPreservingListBuilder;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.render.DonorTintResolver;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftTopSurfaceResolver;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionSheetMethodPlan;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/**
 * 中文：由 Fusion 支持的动态模型；Fusion 决定纹理方向和八邻域状态，AutoBlend 只选择已解析方法并把不可变补丁计划应用到 Fusion 可变 Quad。
 *
 * <p>English: Fusion-backed dynamic model; Fusion determines texture orientation
 * and eight-neighbor state while AutoBlend selects the resolved method and
 * applies the immutable patch plan to Fusion mutable quads.
 */
final class FusionConnectedBlockStateModel
        extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<QueryContext> QUERY_CONTEXT =
            new ModelProperty<>();
    private static final VertexFormat BLOCK_FORMAT =
            DefaultVertexFormat.BLOCK;
    private static final int STRIDE_INTS =
            BLOCK_FORMAT.getVertexSize() / 4;
    private static final int COLOR_OFFSET_INTS =
            3 /* DefaultVertexFormat.BLOCK color int offset */;

    private final BlockState bakedState;
    private final ConcurrentMap<
            ProcessorKey,
            Optional<FusionNativeQuadProcessor>>
            processors = new ConcurrentHashMap<>();

    FusionConnectedBlockStateModel(
            BakedModel delegate,
            BlockState bakedState) {
        super(Objects.requireNonNull(delegate, "delegate"));
        this.bakedState =
                Objects.requireNonNull(bakedState, "bakedState");
    }

    /**
     * 中文：Forge 21.1 通过 ModelData 传递每方块渲染上下文。
     *
     * English: Forge 21.1 delivers the per-block render context through ModelData.
     */
    @Override
    public ModelData getModelData(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ModelData existing) {
        // 中文：先保留 Fusion/其他 delegate 写入的每方块 ModelData，再叠加查询上下文。
        // English: Preserve per-block ModelData written by Fusion/other delegates before
        // adding this wrapper's query context.
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
     * 中文：1.20.1 的 BakedQuad 不保留 Fusion MutableQuad.renderTypes；可能生成
     * overlay 的模型必须额外广告 CUTOUT pass，防止透明遮罩在 SOLID/CUTOUT_MIPPED
     * 原模型 pass 中被当作不透明像素绘制为黑色。
     *
     * <p>English: A 1.20.1 BakedQuad does not retain Fusion MutableQuad.renderTypes.
     * Models that may emit overlays therefore advertise a dedicated CUTOUT pass so the
     * transparent mask is never rendered as opaque pixels in the delegate SOLID or
     * CUTOUT_MIPPED pass.
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
                FusionRenderPassPolicy.advertisedTypes(
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
        long seed = random.nextLong();
        RuleRuntime.Snapshot rules =
                generation.selectors();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                generation.surfaces();
        boolean needsOverlay = needsOverlay(
                generation,
                state,
                EngineQueryRouter.select(
                        state,
                        generation));
        ChunkRenderTypeSet delegateTypes =
                originalModel.getRenderTypes(
                        state,
                        RandomSource.create(seed),
                        modelData);
        FusionRenderPassPolicy.PassDecision decision =
                FusionRenderPassPolicy.decision(
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
                basePass,
                overlayPass);
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
            MinecraftSurfaceCatalog.Snapshot surfaces,
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
                    if (includeBase) {
                        output.add(quad);
                    }
                    continue;
                }
                Optional<FaceSurface> face =
                        surfaces.face(
                                state,
                                quad.getDirection(),
                                quad.getDirection(),
                                sprite);
                // 中文：Fusion 的 TextureInstance 由精确源精灵初始化；缺少该表面时必须原样透传。
                // English: Fusion initializes its TextureInstance from the exact source sprite; a missing surface must pass through unchanged.
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
                // 中文：与重载期精灵规划共享一次 auto 解析，避免把项目扩展方法直接交给 Fusion 原生布局。
                // English: Share the reload-time auto resolution so the project extension is never passed directly to Fusion's native layout.
                ConnectionMethod method = resolveMethod(
                        selection.orElseThrow(),
                        state,
                        inferenceSurface);
                if (method == ConnectionMethod.TOP) {
                    if (includeBase) {
                        BakedQuad top = MinecraftTopSurfaceResolver
                                .resolve(
                                        level,
                                        pos,
                                        state,
                                        quad.getDirection(),
                                        rules.rules(),
                                        surfaces)
                                .map(topSprite ->
                                        FusionNativeQuadProcessor
                                                .retexture(
                                                        quad,
                                                        topSprite))
                                .orElse(quad);
                        output.add(top);
                    }
                    continue;
                }
                if (replacementMethod(method)) {
                    if (!includeBase) {
                        continue;
                    }
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
                if (includeBase) {
                    output.add(quad);
                }
                if (!includeOverlay
                        || !overlayMethod(method)
                        || !surface.fullFace()
                        || !surface.facts().alphaOpaque().isTrue()) {
                    continue;
                }
                boolean occlusionEarlyExit = cullBucket != null
                        && level.getBlockState(
                                pos.relative(cullBucket))
                        .isSolidRender(
                                level,
                                pos.relative(cullBucket));
                if (occlusionEarlyExit) {
                    continue;
                }
                List<Donor> donors = selectDonors(
                        level,
                        pos,
                        quad.getDirection(),
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
     * 中文：配置与隐式 AUTO 属于复合方块面的决策；Fusion 仍绑定精确源精灵，但方法必须复用首轮准备的首选表面结果。
     *
     * English: Config and implicit AUTO are composite-face decisions; Fusion
     * still binds the exact source sprite, while the method reuses the
     * preferred surface result prepared before stitching.
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
        List<BakedQuad> processed = processor.orElseThrow()
                .process(
                        level,
                        pos,
                        donor.state(),
                        seed);
        output.addAll(applyOverlayColor(
                processed,
                tint));
    }

    /**
     * 中文：Fusion 1.20.1 MutableQuad 没有颜色 ABI，overlay ARGB 在发射后写入顶点色。
     *
     * English: Fusion 1.20.1 MutableQuad has no color ABI, so the overlay ARGB
     * is written into vertex colors after emission.
     */
    private static List<BakedQuad> applyOverlayColor(
            List<BakedQuad> quads,
            int argb) {
        if (quads.isEmpty()) {
            return quads;
        }
        int packed = packedColor(argb);
        ArrayList<BakedQuad> output =
                new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices()
                    .clone();
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                vertices[
                        vertex * STRIDE_INTS
                                + COLOR_OFFSET_INTS] =
                        packed;
            }
            output.add(new BakedQuad(
                    vertices,
                    quad.getTintIndex(),
                    quad.getDirection(),
                    quad.getSprite(),
                    quad.isShade()));
        }
        return List.copyOf(output);
    }

    private static int packedColor(int argb) {
        int alpha = (argb >> 24) & 0xFF;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (alpha << 24)
                | (blue << 16)
                | (green << 8)
                | red;
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

    /**
     * 中文：当前代次下该 state 是否可能由 Fusion AutoBlend 发射 overlay。
     *
     * <p>English: Whether Fusion AutoBlend may emit an overlay for this state in the
     * current reload generation.
     */
    private static boolean needsOverlay(
            ReloadPublication.Generation generation,
            BlockState state,
            Optional<EngineQuerySelection> summary) {
        if (summary.isEmpty()) {
            return false;
        }
        EngineQuerySelection selection = summary.orElseThrow();
        if (selection.family() != EngineFamily.FUSION
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
                ConnectionMethod resolved = selection.preparedMethods()
                        .method(
                                state,
                                face.direction(),
                                face.sprite().contents().name())
                        .orElse(face.inferredMethod());
                if (resolved.overlayCapable()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 中文：额外 CUTOUT pass 的只读几何输入。该输入只用于计算 overlay，绝不直接返回。
     *
     * <p>English: Read-only geometry input for the extra CUTOUT pass. It is used only
     * to compute overlays and is never returned directly.
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
