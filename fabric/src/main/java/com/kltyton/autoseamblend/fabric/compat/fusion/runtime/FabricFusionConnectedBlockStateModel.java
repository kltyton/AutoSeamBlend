package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionNativeQuadProcessor;
import com.kltyton.autoseamblend.compat.fusion.runtime.texture.FusionGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.fabric.runtime.render.FabricQuadEmitting;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftTopSurfaceResolver;
import com.kltyton.autoseamblend.runtime.render.DonorTintResolver;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionSheetMethodPlan;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.supermartijn642.fusion.model.WrappedBakedModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：由 Fusion 支持的动态模型；Fusion 决定纹理方向和八邻域状态，AutoBlend 只选择已解析
 * 方法并把不可变补丁计划应用到 Fusion 可变 Quad。
 *
 * <p>English: Fusion-backed dynamic model. Fusion determines texture orientation
 * and eight-neighbor state; AutoBlend only selects the resolved method and
 * applies the immutable patch plan to Fusion mutable quads.
 */
public final class FabricFusionConnectedBlockStateModel
        extends WrappedBakedModel {
    private final BlockState bakedState;
    private final ConcurrentMap<
                    ProcessorKey,
                    Optional<FusionNativeQuadProcessor>>
            processors = new ConcurrentHashMap<>();

    public FabricFusionConnectedBlockStateModel(
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
        // 中文：每次捕获按当前方块图集解析一次 SpriteFinder，只由本次短生命周期的捕获
        // 变换持有；资源重载重新缝合图集后不会残留旧实例。
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
        // 中文：capture 是装到传入真实 RenderContext 上的最外层 QuadTransform，必须在
        // super 发射链之前 push；PaneCullingModel 在 super 调用链内把 PaneCapCullTransform
        // push 到同一真实栈（晚于 capture），Indigo LIFO 保证 PaneCap 先运行、capture
        // 看到 pane 变换后的最终状态，记录 BakedQuad+material+cullFace+nominalFace+tag，
        // 然后 return false，阻止 capture 以下的已有外层 transform 在捕获期重复运行。
        // English: The capture is the outermost QuadTransform installed on the incoming
        // real RenderContext, pushed before the super emission chain; PaneCullingModel
        // pushes PaneCapCullTransform onto the same real stack inside the super chain
        // (later than the capture), so Indigo LIFO runs PaneCap first and the capture
        // records the final BakedQuad plus material/cullFace/nominalFace/tag, then
        // returns false so pre-existing outer transforms below the capture never run
        // during capture.
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
        // 中文：capture 已从真实 context 移除后才回放，已有外层 transform 恰好执行一次。
        // English: Replay runs only after the capture is removed from the real context, so
        // pre-existing outer transforms execute exactly once.
        QuadEmitter output = context.getEmitter();
        for (CapturedQuad captured : capturedQuads) {
            // 中文：几何/精灵/方向用于查询与生成；材质与 cull 面只用捕获值回放。
            // English: Geometry, sprite, and direction drive queries and generation; only
            // the captured material and cullFace are used for replay.
            BakedQuad quad = captured.quad();
            TextureAtlasSprite sprite =
                    quad.getSprite();
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
                            != EngineFamily.FUSION
                    || !selection.orElseThrow()
                            .runsAutoBlend()) {
                prepareEmission(
                                output,
                                captured,
                                quad,
                                captured.material())
                        .emit();
                continue;
            }
            Optional<FaceSurface> face =
                    surfaces.face(
                            state,
                            quad.getDirection(),
                            captured.nominalFace(),
                            sprite);
            if (face.isEmpty()) {
                prepareEmission(
                                output,
                                captured,
                                quad,
                                captured.material())
                        .emit();
                continue;
            }
            FaceSurface surface = face.orElseThrow();
            FaceSurface inferenceSurface =
                    surfaces.preferredFace(
                                    state,
                                    surface.direction())
                            .orElse(surface);
            ConnectionMethod method = resolveMethod(
                    selection.orElseThrow(),
                    state,
                    inferenceSurface);
            if (method == ConnectionMethod.TOP) {
                BakedQuad top =
                        MinecraftTopSurfaceResolver
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
                prepareEmission(
                                output,
                                captured,
                                top,
                                captured.material())
                        .emit();
                continue;
            }
            if (FusionSheetMethodPlan.isReplacement(method)) {
                Optional<FusionNativeQuadProcessor> processor =
                        processor(
                                quad,
                                surface.sprite(),
                                state.getBlock(),
                                method,
                                surface.overlayProfile(),
                                Optional.empty(),
                                generation,
                                rules);
                if (processor.isEmpty()) {
                    prepareEmission(
                                    output,
                                    captured,
                                    quad,
                                    captured.material())
                            .emit();
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
                    prepareEmission(
                                    output,
                                    captured,
                                    quad,
                                    captured.material())
                            .emit();
                } else {
                    replacements.forEach(replacement ->
                            prepareEmission(
                                            output,
                                            captured,
                                            replacement,
                                            captured.material())
                                    .emit());
                }
                continue;
            }
            prepareEmission(
                            output,
                            captured,
                            quad,
                            captured.material())
                    .emit();
            if (!method.overlayCapable()
                    || !surface.fullFace()
                    || !surface.facts()
                            .alphaOpaque()
                            .isTrue()) {
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
                int tint = DonorTintResolver.resolve(
                        donor.state(),
                        level,
                        pos,
                        donor.surface().tintIndex());
                Optional<FusionNativeQuadProcessor> overlay =
                        processor(
                                quad,
                                donor.surface().sprite(),
                                donor.state().getBlock(),
                                donor.method(),
                                donor.surface()
                                        .overlayProfile(),
                                Optional.of(tint),
                                generation,
                                rules);
                if (overlay.isEmpty()) {
                    continue;
                }
                overlay.orElseThrow()
                        .process(
                                level,
                                pos,
                                donor.state(),
                                seed)
                        .forEach(replacement ->
                                emitOverlayQuad(
                                        output,
                                        captured,
                                        replacement,
                                        tint));
            }
        }
    }

    /**
     * 中文：最终发射前的统一准备：用 captured material 与 cullFace 重建 quad，再恢复
     * captured nominalFace/tag。BakedQuad 往返丢失这四个事实（1.20.1 fromVanilla 把
     * nominalFace 重置为 quad 方向、tag 置 0），PaneCapCullTransform 写入的端盖剔除桶
     * 必须在这里重新挂回；passthrough/top/native/none/empty/overlay 全部发射分支共用。
     *
     * <p>English: Shared final-emission preparation: rebuilds the quad with the captured
     * material and cullFace, then restores the captured nominalFace/tag. The BakedQuad
     * round-trip loses all four facts (1.20.1 fromVanilla resets nominalFace to the quad
     * direction and tag to 0), so the pane cap cull bucket written by PaneCapCullTransform
     * must be re-applied here; passthrough/top/native/none/empty/overlay all share it.
     */
    private static QuadEmitter prepareEmission(
            QuadEmitter emitter,
            CapturedQuad source,
            BakedQuad output,
            RenderMaterial material) {
        QuadEmitter prepared =
                FabricQuadEmitting.fromBakedQuad(
                        emitter,
                        output,
                        material,
                        source.cullFace());
        prepared.nominalFace(source.nominalFace());
        prepared.tag(source.tag());
        return prepared;
    }

    private static void emitOverlayQuad(
            QuadEmitter emitter,
            CapturedQuad source,
            BakedQuad replacement,
            int tint) {
        // 中文：overlay 必须显式 CUTOUT 材质（透明区不因 SOLID 渲染变黑），但 cullFace/
        // nominalFace/tag 仍恢复 captured source（与 26.1.2 accepted replay 等价）。
        // English: Overlay must use an explicit CUTOUT material (transparent areas do not
        // render black through SOLID) while still restoring the captured source
        // cullFace/nominalFace/tag (equivalent to the accepted 26.1.2 replay).
        QuadEmitter output =
                prepareEmission(
                        emitter,
                        source,
                        replacement,
                        FabricQuadEmitting.cutoutMaterial());
        // 中文：Fusion 1.3.12 Fabric 在发射时才解析颜色；固定 overlay ARGB 直接写入顶点色。
        // English: Fusion 1.3.12 Fabric resolves colors at emission; write the
        // fixed overlay ARGB into vertex colors.
        for (int vertex = 0;
                vertex < 4;
                vertex++) {
            output.color(vertex, tint);
        }
        output.emit();
    }

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
            com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet<Block>
                    rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        return OverlayDonorResolution.resolveAll(
                level,
                pos,
                face,
                receiver,
                rules,
                surfaces,
                EngineFamily.FUSION,
                OverlayDonorResolution.planarDirections(face));
    }

    /**
     * 中文：捕获的 quad 及其 transform 后真实材质/cull/nominal/tag；BakedQuad 往返会丢失
     * 后四个事实，回放阶段必须全部恢复。
     * English: Captured quad with its post-transform material/cullFace/nominalFace/tag;
     * the BakedQuad round-trip loses all four facts, which replay must restore.
     */
    private record CapturedQuad(
            BakedQuad quad,
            RenderMaterial material,
            Direction cullFace,
            Direction nominalFace,
            int tag) {}

    private record ProcessorKey(
            RuleRuntime.Snapshot ruleSnapshot,
            BakedQuad quad,
            TextureAtlasSprite sprite,
            Block block,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            Optional<Integer> overlayTint) {}
}
