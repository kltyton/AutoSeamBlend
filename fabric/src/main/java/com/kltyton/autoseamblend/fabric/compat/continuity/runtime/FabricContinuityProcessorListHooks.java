package com.kltyton.autoseamblend.fabric.compat.continuity.runtime;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityProcessorListHooks;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.List;
import me.pepperbell.continuity.client.model.QuadProcessors;

/**
 * 中文：保留 NeoForge 已验证的 Continuity 所有权排序和 AutoBlend 追加顺序。
 * English: Preserves NeoForge's verified Continuity ownership ordering and
 * AutoBlend append order on Fabric.
 */
public enum FabricContinuityProcessorListHooks
        implements ContinuityProcessorListHooks.Hooks {
    INSTANCE;

    @Override
    public void begin() {
        FabricContinuityNativeQueryOwnership.beginGeneration(
                ReloadPublication.nextGeneration());
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
