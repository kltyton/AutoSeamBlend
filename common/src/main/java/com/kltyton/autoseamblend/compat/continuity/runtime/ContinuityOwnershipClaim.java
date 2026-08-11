package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：Continuity 已接受处理器投影到共享所有权状态机的不可变输入。
 *
 * <p>English: Immutable projection of an accepted Continuity processor into the shared ownership
 * state machine.
 */
public record ContinuityOwnershipClaim(
        SourceTier sourceTier,
        Optional<AutoBlendPolicy> strategyPolicy,
        ConnectionMethod requestedMethod,
        ConnectionMethod resolvedMethod,
        boolean additiveOverlay,
        boolean overlaySelectionPresent,
        List<ContinuityOverlaySlotIntent> overlaySlotIntents) {
    public ContinuityOwnershipClaim {
        Objects.requireNonNull(sourceTier, "sourceTier");
        strategyPolicy = Objects.requireNonNull(strategyPolicy, "strategyPolicy");
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        overlaySlotIntents = List.copyOf(
                Objects.requireNonNull(overlaySlotIntents, "overlaySlotIntents"));
    }

    public ConnectionMethod requestedOrResolved() {
        return requestedMethod == ConnectionMethod.AUTO
                ? ConnectionMethod.AUTO
                : resolvedMethod;
    }
}
