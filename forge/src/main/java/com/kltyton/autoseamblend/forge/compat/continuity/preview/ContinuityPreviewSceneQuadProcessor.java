package com.kltyton.autoseamblend.forge.compat.continuity.preview;

import com.kltyton.autoseamblend.authoring.preview.PreviewSceneQuadProcessor;
import com.kltyton.autoseamblend.forge.compat.continuity.runtime.QuadConversions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.model.QuadProcessors;
import me.pepperbell.continuity.client.model.QuadProcessors.Slice;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：把预览场景的每个 node Quad 送入 NeoContinuity 真实处理器链（同一
 * QuadProcessors 切片与 ProcessingData 语义），不复制任何 CTM 算法。
 *
 * English: Feeds every preview-scene node quad through NeoContinuity's real
 * processor chain (the same QuadProcessors slice and ProcessingData semantics),
 * without copying any CTM algorithm.
 */
public final class ContinuityPreviewSceneQuadProcessor
        implements PreviewSceneQuadProcessor {
    private static final int MAX_PASSES = 16;

    public static final ContinuityPreviewSceneQuadProcessor INSTANCE =
            new ContinuityPreviewSceneQuadProcessor();

    private ContinuityPreviewSceneQuadProcessor() {}

    @Override
    public String engineId() {
        return "continuity";
    }

    @Override
    public List<BakedQuad> process(
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            long randomSeed,
            List<BakedQuad> sourceQuads) {
        PreviewSceneQuadProcessor.requireUnmodifiable(
                sourceQuads);
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(pos, "pos");
        ArrayList<BakedQuad> output =
                new ArrayList<>(sourceQuads.size());
        for (BakedQuad source : sourceQuads) {
            processOne(
                    source,
                    level,
                    state,
                    pos,
                    randomSeed,
                    output);
        }
        if (output.isEmpty()
                && !sourceQuads.isEmpty()) {
            // 中文：绝不把空结果提交给 renderer；保留 raw quads。
            // English: Never submit an empty result to the renderer; keep the raw quads.
            return List.copyOf(sourceQuads);
        }
        return List.copyOf(output);
    }

    private static void processOne(
            BakedQuad source,
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            long randomSeed,
            ArrayList<BakedQuad> output) {
        ContinuityPreviewProcessingContext context =
                new ContinuityPreviewProcessingContext();
        MutableQuadView quad =
                QuadConversions.fromBakedQuad(source);
        TextureAtlasSprite sprite =
                source.getSprite();
        Slice slice =
                QuadProcessors.getCache(state)
                        .apply(sprite);
        QuadProcessor.ProcessingResult result =
                runProcessors(
                        slice.processors(),
                        quad,
                        sprite,
                        level,
                        state,
                        pos,
                        randomSeed,
                        0,
                        context);
        int pass = 0;
        while (result
                        == QuadProcessor.ProcessingResult.NEXT_PASS
                && pass < MAX_PASSES) {
            pass++;
            result = runProcessors(
                    slice.multipassProcessors(),
                    quad,
                    sprite,
                    level,
                    state,
                    pos,
                    randomSeed,
                    pass,
                    context);
        }
        if (result
                != QuadProcessor.ProcessingResult.DISCARD) {
            output.add(
                    quad.toBakedQuad(sprite));
        }
        replayExtraQuads(
                context,
                sprite,
                output);
    }

    /**
     * 中文：按 NeoContinuity ProcessingContextImpl.outputTo 的契约回放额外 Quad
     * （emitterConsumers → addMesh → meshBuilder.build），转成 BakedQuad 追加到输出；
     * 声明了额外 Quad 但零产出时保持输出不变，不产生日志。
     *
     * English: Replays extra quads following NeoContinuity's
     * ProcessingContextImpl.outputTo contract (emitterConsumers → added meshes →
     * meshBuilder.build), converts them to BakedQuads and appends them to the
     * output; zero production keeps the output unchanged with no logging.
     */
    private static void replayExtraQuads(
            ContinuityPreviewProcessingContext context,
            TextureAtlasSprite sprite,
            ArrayList<BakedQuad> output) {
        MeshBuilder builder =
                RendererAccess.INSTANCE
                        .getRenderer()
                        .meshBuilder();
        QuadEmitter target = builder.getEmitter();
        if (!context.drainExtraQuads(target)) {
            return;
        }
        Mesh mesh = builder.build();
        mesh.forEach(view ->
                output.add(
                        view.toBakedQuad(sprite)));
    }

    private static QuadProcessor.ProcessingResult
            runProcessors(
                    QuadProcessor[] processors,
                    MutableQuadView quad,
                    TextureAtlasSprite sprite,
                    BlockAndTintGetter level,
                    BlockState state,
                    BlockPos pos,
                    long randomSeed,
                    int pass,
                    ContinuityPreviewProcessingContext context) {
        QuadProcessor.ProcessingResult result =
                QuadProcessor.ProcessingResult.NEXT_PROCESSOR;
        for (QuadProcessor processor : processors) {
            result = processor.processQuad(
                    quad,
                    sprite,
                    level,
                    state,
                    state,
                    pos,
                    () -> RandomSource.create(randomSeed),
                    pass,
                    context);
            if (result
                            == QuadProcessor.ProcessingResult.STOP
                    || result
                            == QuadProcessor.ProcessingResult.DISCARD) {
                break;
            }
        }
        return result;
    }
}
