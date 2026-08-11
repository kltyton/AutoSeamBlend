package com.kltyton.autoseamblend.fabric.compat.continuity.runtime;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityCachingPredicates;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityNativeOwnershipState;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityOwnershipClaim;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.model.QuadProcessors;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：一个 Continuity 网格线程的查询局部原生规则所有权。
 * English: Query-local native rule ownership for one Continuity meshing thread.
 */
public final class FabricNativeOwnershipTracker {
    private static final ThreadLocal<ContinuityNativeOwnershipState> CURRENT =
            ThreadLocal.withInitial(
                    ContinuityNativeOwnershipState::new);
    private static final QuadProcessor RESET_PROCESSOR =
            new ResetProcessor();

    private FabricNativeOwnershipTracker() {}

    public static QuadProcessors.ProcessorHolder
            resetHolder() {
        return new QuadProcessors.ProcessorHolder(
                RESET_PROCESSOR,
                ContinuityCachingPredicates.INSTANCE);
    }

    static void claim(
            FabricContinuityProcessorMetadata metadata) {
        CURRENT.get().claim(project(metadata));
    }

    static boolean shouldSkip(
            FabricContinuityProcessorMetadata metadata) {
        return CURRENT.get().shouldSkip(project(metadata));
    }

    static void begin(
            FabricContinuityProcessorMetadata metadata,
            List<Integer> selectedOverlaySlots) {
        CURRENT.get().begin(
                project(metadata),
                selectedOverlaySlots);
    }

    static void end() {}

    static List<Integer> filterAutoBlendOverlaySlots(
            List<Integer> requested) {
        return CURRENT.get()
                .filterAutoBlendOverlaySlots(
                        requested);
    }

    static boolean allowsAutoBlend(
            RuleRuntime.Snapshot rules,
            Block target) {
        return CURRENT.get().allowsAutoBlend(
                rules,
                target);
    }

    static Optional<ConnectionMethod> effectiveMethod() {
        return CURRENT.get().effectiveMethod();
    }

    static boolean nativeAuthorExact() {
        return CURRENT.get().nativeAuthorExact();
    }

    private static ContinuityOwnershipClaim project(
            FabricContinuityProcessorMetadata metadata) {
        return new ContinuityOwnershipClaim(
                metadata.sourceTier(),
                metadata.strategyPolicy(),
                metadata.requestedMethod(),
                metadata.resolvedMethod(),
                metadata.additiveOverlay(),
                metadata.overlaySelection().isPresent(),
                metadata.overlaySlotIntents());
    }

    private static final class ResetProcessor
            implements QuadProcessor {
        @Override
        public ProcessingResult processQuad(
                MutableQuadView quad,
                TextureAtlasSprite sprite,
                BlockAndTintGetter level,
                BlockState appearanceState,
                BlockState state,
                BlockPos pos,
                Supplier<RandomSource> random,
                int pass,
                ProcessingContext context) {
            CURRENT.get().reset();
            return ProcessingResult.NEXT_PROCESSOR;
        }
    }
}
