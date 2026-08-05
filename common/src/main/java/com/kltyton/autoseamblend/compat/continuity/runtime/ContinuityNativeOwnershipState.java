package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.block.Block;

/**
 * 中文：一个网格线程的 Continuity 查询局部所有权、覆盖层槽位和补全策略状态。
 *
 * <p>English: Query-local Continuity ownership, overlay-slot, and completion-policy state for one
 * meshing thread.
 */
public final class ContinuityNativeOwnershipState {
    private boolean nativeAuthorExact;
    private Optional<AutoBlendPolicy> nativeAuthorPolicy = Optional.empty();
    private Optional<ConnectionMethod> nativeAuthorMethod = Optional.empty();
    private Optional<AutoBlendPolicy> managedPolicy = Optional.empty();
    private boolean managedExact;
    private Optional<ConnectionMethod> managedMethod = Optional.empty();
    private boolean protectedContent;
    private final BitSet occupiedOverlaySlots = new BitSet(17);

    public void claim(ContinuityOwnershipClaim claim) {
        if (claim.sourceTier() == SourceTier.NATIVE_AUTHOR) {
            nativeAuthorExact = true;
            if (nativeAuthorPolicy.isEmpty()) {
                nativeAuthorPolicy = claim.strategyPolicy();
            }
            if (nativeAuthorMethod.isEmpty()) {
                nativeAuthorMethod = Optional.of(claim.requestedOrResolved());
            }
        }
        if (managedPolicy.isEmpty()
                && (claim.sourceTier() == SourceTier.MANAGED_COMPATIBILITY
                        || claim.sourceTier() == SourceTier.MANAGED_NON_COMPATIBILITY)) {
            managedExact = true;
            managedPolicy = claim.strategyPolicy();
            managedMethod = Optional.of(claim.requestedOrResolved());
        }
    }

    public boolean shouldSkip(ContinuityOwnershipClaim claim) {
        boolean authorBlocksCompletion =
                nativeAuthorPolicy.filter(policy -> !policy.allowsCompletion()).isPresent();
        boolean lowerPolicyBlocksCompletion =
                nativeAuthorPolicy.isEmpty()
                        && managedPolicy.filter(policy -> !policy.allowsCompletion()).isPresent();
        return nativeAuthorExact
                && (authorBlocksCompletion || lowerPolicyBlocksCompletion)
                && claim.sourceTier() != SourceTier.NATIVE_AUTHOR;
    }

    public void begin(
            ContinuityOwnershipClaim claim,
            List<Integer> selectedOverlaySlots) {
        if (!claim.additiveOverlay()) {
            return;
        }
        if (!claim.overlaySelectionPresent()) {
            if (claim.overlaySlotIntents().stream()
                    .anyMatch(intent -> intent != ContinuityOverlaySlotIntent.FILLABLE)) {
                protectedContent = true;
            }
            return;
        }
        for (int slot : selectedOverlaySlots) {
            if (slot < 0 || slot >= claim.overlaySlotIntents().size()) {
                protectedContent = true;
                return;
            }
            ContinuityOverlaySlotIntent intent = claim.overlaySlotIntents().get(slot);
            if (intent == ContinuityOverlaySlotIntent.FILLABLE) {
                continue;
            }
            if (intent == ContinuityOverlaySlotIntent.PRESENT) {
                occupiedOverlaySlots.set(slot);
            } else {
                protectedContent = true;
            }
        }
    }

    public List<Integer> filterAutoBlendOverlaySlots(List<Integer> requested) {
        if (protectedContent) {
            return List.of();
        }
        ArrayList<Integer> missing = new ArrayList<>(requested.size());
        for (int slot : requested) {
            if (!occupiedOverlaySlots.get(slot)) {
                missing.add(slot);
            }
        }
        return List.copyOf(missing);
    }

    public boolean allowsAutoBlend(
            RuleRuntime.Snapshot snapshot,
            Block target) {
        if (protectedContent) {
            return false;
        }
        AutoBlendPolicy policy = nativeAuthorPolicy.orElseGet(() ->
                managedPolicy.orElseGet(() -> configPolicy(snapshot, target)));
        if (nativeAuthorExact) {
            return policy.allowsCompletion();
        }
        return !managedExact || policy.allowsCompletion();
    }

    public Optional<ConnectionMethod> effectiveMethod() {
        return nativeAuthorMethod.or(() -> managedMethod);
    }

    public boolean nativeAuthorExact() {
        return nativeAuthorExact;
    }

    public void reset() {
        nativeAuthorExact = false;
        nativeAuthorPolicy = Optional.empty();
        nativeAuthorMethod = Optional.empty();
        managedPolicy = Optional.empty();
        managedExact = false;
        managedMethod = Optional.empty();
        protectedContent = false;
        occupiedOverlaySlots.clear();
    }

    private static AutoBlendPolicy configPolicy(
            RuleRuntime.Snapshot snapshot,
            Block target) {
        ConnectionRuleSet<Block> rules = snapshot.rules();
        return rules.configuredSelector(target)
                .filter(selector -> !rules.isExcluded(
                        target,
                        selector.method(),
                        selector.mode()))
                .map(selector -> selector.mode()
                                == ConnectionRuleSet.ResourcePackMode.COMPATIBILITY
                        ? AutoBlendPolicy.ALLOW_COMPLETION
                        : AutoBlendPolicy.NATIVE_EXCLUSIVE)
                .orElseGet(() -> snapshot.automaticDiscovery()
                                && !rules.isExcluded(
                                        target,
                                        ConnectionMethod.AUTO,
                                        ConnectionRuleSet.ResourcePackMode.COMPATIBILITY)
                        ? AutoBlendPolicy.ALLOW_COMPLETION
                        : AutoBlendPolicy.NATIVE_EXCLUSIVE);
    }
}
