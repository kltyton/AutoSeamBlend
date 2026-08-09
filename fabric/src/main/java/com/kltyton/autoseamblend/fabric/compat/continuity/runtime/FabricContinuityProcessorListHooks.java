package com.kltyton.autoseamblend.fabric.compat.continuity.runtime;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityProcessorListHooks;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.List;
import me.pepperbell.continuity.client.model.QuadProcessors;

/**
 * 中文：保留已验证的 Continuity 所有权排序和 AutoBlend 追加顺序。
 * English: Preserves the verified Continuity ownership ordering and
 * AutoBlend append order.
 */
public enum FabricContinuityProcessorListHooks
        implements ContinuityProcessorListHooks.Hooks {
    INSTANCE;

    private long currentGeneration = -1;

    @Override
    public void begin() {
        currentGeneration =
                ReloadPublication.nextGeneration();
        FabricContinuityNativeQueryOwnership
                .beginGeneration(currentGeneration);
    }

    @Override
    public void complete(
            List<QuadProcessors.ProcessorHolder> holders) {
        holders.replaceAll(
                FabricOwnershipTrackingProcessor::wrap);
        holders.sort(
                FabricOwnershipTrackingProcessor
                        ::compareSourcePrecedence);
        holders.addFirst(
                FabricNativeOwnershipTracker
                        .resetHolder());
        holders.add(
                FabricContinuityAutoBlendProcessor
                        .holder());
        FabricContinuityNativeQueryOwnership
                .stageGeneration();
    }
}
