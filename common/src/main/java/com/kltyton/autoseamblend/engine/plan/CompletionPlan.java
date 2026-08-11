package com.kltyton.autoseamblend.engine.plan;

import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.NativeOwnership;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.selection.method.ConfiguredMethodPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 中文：运行时、预览、实体化和 baked 导出原样使用的不可变槽位答案。 / English: Immutable slot answer consumed unchanged by runtime, preview, materialize and baked export. */
public record CompletionPlan(
        Outcome outcome,
        ConfiguredMethodPlan method,
        AutoBlendPolicy policy,
        boolean hasAcceptedNativeAuthorExactMatch,
        List<NativeOwnership> orderedClaims,
        Optional<NativeOwnership> conservativeBlocker,
        List<NativeSlot> protectedSlots,
        List<Integer> missingSlots,
        String reason) {
    public CompletionPlan {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(policy, "policy");
        orderedClaims = List.copyOf(Objects.requireNonNull(orderedClaims, "orderedClaims"));
        conservativeBlocker = Objects.requireNonNull(conservativeBlocker, "conservativeBlocker");
        protectedSlots = List.copyOf(Objects.requireNonNull(protectedSlots, "protectedSlots"));
        missingSlots = List.copyOf(Objects.requireNonNull(missingSlots, "missingSlots"));
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        for (NativeOwnership claim : orderedClaims) {
            if (claim.match() != NativeOwnership.Match.MATCH) {
                throw new IllegalArgumentException("ordered claims must contain exact matches only");
            }
            if (effectivePublicMethodValue(claim) != method.resolvedMethod()) {
                throw new IllegalArgumentException(
                        "ordered claims must prove the completion plan's concrete public method");
            }
            for (NativeSlot slot : claim.slots()) {
                if (!method.domain().slots().contains(slot.index())) {
                    throw new IllegalArgumentException(
                            "ordered claim slots must belong to the concrete method domain");
                }
            }
        }
        for (NativeSlot slot : protectedSlots) {
            if (!method.domain().slots().contains(slot.index())) {
                throw new IllegalArgumentException(
                        "protected slots must belong to the concrete method domain");
            }
        }
        conservativeBlocker.ifPresent(blocker -> {
            if (blocker.match() != NativeOwnership.Match.CONSERVATIVE_UNKNOWN) {
                throw new IllegalArgumentException("conservative blocker must be an unknown result");
            }
        });
    }

    public static CompletionPlan create(
            ConfiguredMethodPlan method,
            AutoBlendPolicy policy,
            boolean hasAcceptedNativeAuthorExactMatch,
            List<NativeOwnership> orderedExactClaims,
            Optional<NativeOwnership> conservativeBlocker) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(policy, "policy");
        List<NativeOwnership> claims = List.copyOf(
                Objects.requireNonNull(orderedExactClaims, "orderedExactClaims"));
        conservativeBlocker = Objects.requireNonNull(conservativeBlocker, "conservativeBlocker");
        ArrayList<NativeOwnership> methodClaimsBuilder = new ArrayList<>();
        for (NativeOwnership claim : claims) {
            if (claim.match() != NativeOwnership.Match.MATCH) {
                throw new IllegalArgumentException("ordered exact claims must contain exact matches only");
            }
            if (effectivePublicMethodValue(claim) == method.resolvedMethod()) {
                methodClaimsBuilder.add(restrictSlotsToDomain(claim, method));
            }
        }
        List<NativeOwnership> methodClaims = List.copyOf(methodClaimsBuilder);

        if (conservativeBlocker.isPresent()) {
            List<NativeSlot> unknownSlots = method.domain().slots().stream()
                    .map(index -> new NativeSlot(index, NativeSlotIntent.UNKNOWN, Optional.empty()))
                    .toList();
            return new CompletionPlan(
                    Outcome.CONSERVATIVE_NATIVE,
                    method,
                    policy,
                    hasAcceptedNativeAuthorExactMatch,
                    methodClaims,
                    conservativeBlocker,
                    unknownSlots,
                    List.of(),
                    "higher_priority_native_result_unknown");
        }
        if (method.resolvedMethod() == ConnectionMethod.NONE) {
            return new CompletionPlan(
                    Outcome.PASSTHROUGH,
                    method,
                    policy,
                    hasAcceptedNativeAuthorExactMatch,
                    methodClaims,
                    Optional.empty(),
                    protectedSlots(methodClaims, method),
                    List.of(),
                    "method_none");
        }

        List<NativeOwnership> activeClaims = methodClaims;
        if (!policy.allowsCompletion() && hasAcceptedNativeAuthorExactMatch) {
            ArrayList<NativeOwnership> nativeClaims = new ArrayList<>();
            for (NativeOwnership claim : methodClaims) {
                if (claim.source()
                        .map(source -> source.tier() == SourceTier.NATIVE_AUTHOR)
                        .orElse(false)) {
                    nativeClaims.add(claim);
                }
            }
            activeClaims = List.copyOf(nativeClaims);
        }
        List<NativeSlot> protectedSlots = protectedSlots(activeClaims, method);
        if (!policy.allowsCompletion() && hasAcceptedNativeAuthorExactMatch) {
            return new CompletionPlan(
                    Outcome.NATIVE_ONLY,
                    method,
                    policy,
                    true,
                    activeClaims,
                    Optional.empty(),
                    protectedSlots,
                    List.of(),
                    "accepted_native_author_exact_match_is_exclusive");
        }

        java.util.HashSet<Integer> protectedIndices = new java.util.HashSet<>();
        for (NativeSlot slot : protectedSlots) {
            protectedIndices.add(slot.index());
        }
        java.util.ArrayList<Integer> missingBuilder = new java.util.ArrayList<>();
        for (Integer slot : method.domain().slots()) {
            if (!protectedIndices.contains(slot)) {
                missingBuilder.add(slot);
            }
        }
        List<Integer> missing = List.copyOf(missingBuilder);
        return new CompletionPlan(
                hasAcceptedNativeAuthorExactMatch ? Outcome.COMPLEMENT : Outcome.FULL,
                method,
                policy,
                hasAcceptedNativeAuthorExactMatch,
                activeClaims,
                Optional.empty(),
                protectedSlots,
                missing,
                hasAcceptedNativeAuthorExactMatch
                        ? "preserve_native_author_and_fill_missing_slots"
                        : "full_autoblend_with_nonfillable_content_protected");
    }

    private static List<NativeSlot> protectedSlots(
            List<NativeOwnership> claims,
            ConfiguredMethodPlan method) {
        LinkedHashMap<Integer, NativeSlot> bySlot = new LinkedHashMap<>();
        for (NativeOwnership claim : claims) {
            for (NativeSlot slot : claim.slots()) {
                if (!method.domain().slots().contains(slot.index())
                        || !slot.intent().protectedIntent()) continue;
                bySlot.putIfAbsent(slot.index(), slot);
            }
        }
        ArrayList<NativeSlot> protectedSlots = new ArrayList<>(bySlot.values());
        protectedSlots.sort(Comparator.comparingInt(NativeSlot::index));
        return List.copyOf(protectedSlots);
    }

    private static NativeOwnership restrictSlotsToDomain(
            NativeOwnership claim,
            ConfiguredMethodPlan method) {
        ArrayList<NativeSlot> domainSlotsBuilder = new ArrayList<>();
        for (NativeSlot slot : claim.slots()) {
            if (method.domain().slots().contains(slot.index())) {
                domainSlotsBuilder.add(slot);
            }
        }
        List<NativeSlot> domainSlots = List.copyOf(domainSlotsBuilder);
        if (domainSlots.size() == claim.slots().size()) return claim;
        return new NativeOwnership(
                claim.match(),
                claim.source(),
                claim.requestedMethod(),
                claim.resolvedMethod(),
                domainSlots,
                claim.reason());
    }

    private static ConnectionMethod effectivePublicMethodValue(NativeOwnership claim) {
        ConnectionMethod requested = claim.requestedMethod().orElse(null);
        if (requested != null && requested != ConnectionMethod.AUTO) {
            return requested;
        }
        ConnectionMethod resolved = claim.resolvedMethod().orElse(null);
        return resolved == null || resolved == ConnectionMethod.AUTO ? null : resolved;
    }

    public enum Outcome {
        PASSTHROUGH,
        NATIVE_ONLY,
        CONSERVATIVE_NATIVE,
        FULL,
        COMPLEMENT
    }
}
