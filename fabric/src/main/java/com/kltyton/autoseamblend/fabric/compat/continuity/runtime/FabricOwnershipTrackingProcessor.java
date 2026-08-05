package com.kltyton.autoseamblend.fabric.compat.continuity.runtime;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuitySourcePrecedence;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.mixin.continuity.AbstractQuadProcessorAccessor;
import com.kltyton.autoseamblend.mixin.continuity.SimpleQuadProcessorAccessor;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import java.util.Objects;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.model.QuadProcessors;
import me.pepperbell.continuity.client.processor.AbstractQuadProcessor;
import me.pepperbell.continuity.client.processor.ProcessingPredicate;
import me.pepperbell.continuity.client.processor.simple.SimpleQuadProcessor;
import me.pepperbell.continuity.client.util.RenderUtil;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：在追加的 AutoBlend 处理器运行前保留精确原生查询所有权。
 * English: Preserves exact native query ownership before the appended
 * AutoBlend processor runs.
 */
public final class FabricOwnershipTrackingProcessor
        implements QuadProcessor {
    private final QuadProcessor delegate;
    private final ProcessingPredicate queryPredicate;
    private final FabricContinuityProcessorMetadata metadata;

    private FabricOwnershipTrackingProcessor(
            QuadProcessor delegate,
            ProcessingPredicate queryPredicate,
            FabricContinuityProcessorMetadata metadata) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "delegate");
        this.queryPredicate = queryPredicate;
        this.metadata = Objects.requireNonNull(
                metadata,
                "metadata");
    }

    public static QuadProcessors.ProcessorHolder wrap(
            QuadProcessors.ProcessorHolder holder) {
        Objects.requireNonNull(holder, "holder");
        QuadProcessor processor = holder.processor();
        ProcessingPredicate queryPredicate =
                predicate(processor);
        FabricContinuityProcessorMetadata metadata =
                FabricContinuityProcessorMetadata.take(
                        processor);
        FabricContinuityNativeQueryOwnership.register(
                holder.predicates(),
                queryPredicate,
                metadata);
        return new QuadProcessors.ProcessorHolder(
                new FabricOwnershipTrackingProcessor(
                        processor,
                        queryPredicate,
                        metadata),
                holder.predicates());
    }

    public static int compareSourcePrecedence(
            QuadProcessors.ProcessorHolder left,
            QuadProcessors.ProcessorHolder right) {
        return ContinuitySourcePrecedence.compare(
                sourceTier(left),
                sourceTier(right));
    }

    @Override
    public ProcessingResult processQuad(
            MutableQuadView quad,
            TextureAtlasSprite sprite,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState appearanceState,
            BlockState state,
            RandomSource random,
            int pass,
            ProcessingContext context) {
        boolean accepted = queryPredicate != null
                && queryPredicate.shouldProcessQuad(
                        quad,
                        sprite,
                        level,
                        pos,
                        appearanceState,
                        state,
                        context);
        if (accepted) {
            FabricNativeOwnershipTracker.claim(
                    metadata);
            if (FabricNativeOwnershipTracker.shouldSkip(
                    metadata)) {
                return ProcessingResult.NEXT_PROCESSOR;
            }
            if (metadata.sourceTier()
                            != SourceTier.NATIVE_AUTHOR
                    && !metadata
                            .nativeSpritesPresent()) {
                return ProcessingResult.NEXT_PROCESSOR;
            }
        }
        java.util.List<Integer> selectedOverlaySlots =
                accepted
                        ? metadata.overlaySelection()
                                .map(selector -> selector.select(
                                        quad,
                                        sprite,
                                        level,
                                        pos,
                                        appearanceState,
                                        state,
                                        context))
                                .orElseGet(java.util.List::of)
                        : java.util.List.of();
        FabricNativeOwnershipTracker.begin(
                metadata,
                selectedOverlaySlots);
        boolean mayCompleteMissingReplacement =
                accepted
                        && !metadata.additiveOverlay()
                        && !metadata
                                .presentResourceFailedAtlas()
                        && metadata.nativeSlots()
                                .stream()
                                .anyMatch(slot ->
                                        slot.intent()
                                                == NativeSlotIntent.DECLARED_MISSING)
                        && FabricNativeOwnershipTracker
                                .allowsAutoBlend(
                                        RuleRuntime.current(),
                                        state.getBlock());
        BakedQuad original =
                mayCompleteMissingReplacement
                        ? quad.toBakedQuad(sprite)
                        : null;
        ProcessingResult result;
        try {
            result = delegate.processQuad(
                    quad,
                    sprite,
                    level,
                    pos,
                    appearanceState,
                    state,
                    random,
                    pass,
                    context);
        } finally {
            FabricNativeOwnershipTracker.end();
        }
        if (original != null
                && RenderUtil.isMissingSprite(
                        quad.toBakedQuad(sprite)
                                .materialInfo()
                                .sprite())) {
            quad.fromBakedQuad(original);
            return ProcessingResult.NEXT_PROCESSOR;
        }
        boolean ownsQuery =
                accepted
                        || result
                                != ProcessingResult.NEXT_PROCESSOR;
        if (ownsQuery && !accepted) {
            FabricNativeOwnershipTracker.claim(
                    metadata);
        }
        return result;
    }

    private static SourceTier sourceTier(
            QuadProcessors.ProcessorHolder holder) {
        if (holder.processor()
                instanceof FabricOwnershipTrackingProcessor
                        tracking) {
            return tracking.metadata.sourceTier();
        }
        throw new IllegalArgumentException(
                "holder is not ownership-tracked");
    }

    private static ProcessingPredicate predicate(
            QuadProcessor processor) {
        if (processor
                instanceof AbstractQuadProcessor) {
            return ((AbstractQuadProcessorAccessor)
                            processor)
                    .autoseamblend$processingPredicate();
        }
        if (processor
                instanceof SimpleQuadProcessor) {
            return ((SimpleQuadProcessorAccessor)
                            processor)
                    .autoseamblend$processingPredicate();
        }
        return null;
    }
}
