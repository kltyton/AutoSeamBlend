package com.kltyton.autoseamblend.neoforge.compat.continuity.runtime;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityProcessorListHooks;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.List;
import me.pepperbell.continuity.client.model.QuadProcessors;

/**
 * 中文：保留 NeoForge 已验证的 Continuity 所有权排序和 AutoBlend 追加顺序。
 * English: Preserves NeoForge's verified Continuity ownership ordering and AutoBlend append order.
 */
public final class NeoForgeContinuityProcessorListHooks
        implements ContinuityProcessorListHooks.Hooks {
    public static final NeoForgeContinuityProcessorListHooks INSTANCE =
            new NeoForgeContinuityProcessorListHooks();

    private long currentGeneration = -1;

    private NeoForgeContinuityProcessorListHooks() {
    }

    @Override
    public void begin() {
        currentGeneration =
                ReloadPublication.nextGeneration();
        ContinuityNativeQueryOwnership.beginGeneration(
                currentGeneration);
    }

    @Override
    public void complete(List<QuadProcessors.ProcessorHolder> holders) {
        holders.replaceAll(OwnershipTrackingProcessor::wrap);
        holders.sort(OwnershipTrackingProcessor::compareSourcePrecedence);
        holders.addFirst(NativeOwnershipTracker.resetHolder());
        holders.add(ContinuityAutoBlendProcessor.holder());
        ContinuityNativeQueryOwnership.stageGeneration();
    }
}
