package com.kltyton.autoseamblend.neoforge.compat.continuity.runtime;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityCachingPredicates;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityNativeOwnershipState;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityOwnershipClaim;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Optional;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.model.QuadProcessors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/** 中文：一个 NeoContinuity 网格线程的查询局部原生规则所有权。 / English: Query-local native rule ownership for one NeoContinuity meshing thread. */
public final class NativeOwnershipTracker {
    private static final ThreadLocal<ContinuityNativeOwnershipState> CURRENT =
            ThreadLocal.withInitial(ContinuityNativeOwnershipState::new);
    private static final QuadProcessor RESET_PROCESSOR =
            new ResetProcessor();

    private NativeOwnershipTracker() {}

    public static QuadProcessors.ProcessorHolder resetHolder() {
        return new QuadProcessors.ProcessorHolder(
                RESET_PROCESSOR,
                ContinuityCachingPredicates.INSTANCE);
    }

    static void claim(ContinuityProcessorMetadata metadata) {
        CURRENT.get().claim(project(metadata));
    }

    static boolean shouldSkip(
            ContinuityProcessorMetadata metadata) {
        return CURRENT.get().shouldSkip(project(metadata));
    }

    static void begin(
            ContinuityProcessorMetadata metadata,
            List<Integer> selectedOverlaySlots) {
        CURRENT.get().begin(project(metadata), selectedOverlaySlots);
    }

    static void end() {}

    static List<Integer> filterAutoBlendOverlaySlots(
            List<Integer> requested) {
        return CURRENT.get().filterAutoBlendOverlaySlots(requested);
    }

    static boolean allowsAutoBlend(
            RuleRuntime.Snapshot rules,
            Block target) {
        return CURRENT.get().allowsAutoBlend(rules, target);
    }

    static Optional<ConnectionMethod> effectiveMethod() {
        return CURRENT.get().effectiveMethod();
    }

    static boolean nativeAuthorExact() {
        return CURRENT.get().nativeAuthorExact();
    }

    private static ContinuityOwnershipClaim project(
            ContinuityProcessorMetadata metadata) {
        return new ContinuityOwnershipClaim(
                metadata.sourceTier(),
                metadata.strategyPolicy(),
                metadata.requestedMethod(),
                metadata.resolvedMethod(),
                metadata.additiveOverlay(),
                metadata.overlaySelection().isPresent(),
                metadata.overlaySlotIntents());
    }

    private static final class ResetProcessor implements QuadProcessor {
        @Override
        public ProcessingResult processQuad(
                MutableQuad quad,
                TextureAtlasSprite sprite,
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState appearanceState,
                BlockState state,
                RandomSource random,
                int pass,
                ProcessingContext context) {
            CURRENT.get().reset();
            return ProcessingResult.NEXT_PROCESSOR;
        }
    }
}
