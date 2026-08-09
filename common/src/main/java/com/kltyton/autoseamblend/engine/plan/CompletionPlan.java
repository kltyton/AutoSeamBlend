package com.kltyton.autoseamblend.engine.plan;

import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.NativeOwnership;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.selection.method.ConfiguredMethodPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
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
        if (orderedClaims.stream().anyMatch(claim -> claim.match() != NativeOwnership.Match.MATCH)) {
            throw new IllegalArgumentException("ordered claims must contain exact matches only");
        }
        if (orderedClaims.stream().anyMatch(claim -> effectivePublicMethod(claim)
                .filter(value -> value == method.resolvedMethod())
                .isEmpty())) {
            throw new IllegalArgumentException(
                    "ordered claims must prove the completion plan's concrete public method");
        }
        if (orderedClaims.stream()
                .flatMap(claim -> claim.slots().stream())
                .anyMatch(slot -> !method.domain().slots().contains(slot.index()))) {
            throw new IllegalArgumentException("ordered claim slots must belong to the concrete method domain");
        }
        if (protectedSlots.stream().anyMatch(slot -> !method.domain().slots().contains(slot.index()))) {
            throw new IllegalArgumentException("protected slots must belong to the concrete method domain");
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
        if (claims.stream().anyMatch(claim -> claim.match() != NativeOwnership.Match.MATCH)) {
            throw new IllegalArgumentException("ordered exact claims must contain exact matches only");
        }
        List<NativeOwnership> methodClaims = claims.stream()
                .filter(claim -> effectivePublicMethod(claim)
                        .filter(value -> value == method.resolvedMethod())
                        .isPresent())
                .map(claim -> restrictSlotsToDomain(claim, method))
                .toList();

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

        List<NativeOwnership> activeClaims = !policy.allowsCompletion() && hasAcceptedNativeAuthorExactMatch
                ? methodClaims.stream()
                        .filter(claim -> claim.source()
                                .map(source -> source.tier() == SourceTier.NATIVE_AUTHOR)
                                .orElse(false))
                        .toList()
                : methodClaims;
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

        java.util.Set<Integer> protectedIndices = protectedSlots.stream()
                .map(NativeSlot::index)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Integer> missing = method.domain().slots().stream()
                .filter(slot -> !protectedIndices.contains(slot))
                .toList();
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
        return bySlot.values().stream()
                .sorted(Comparator.comparingInt(NativeSlot::index))
                .toList();
    }

    private static Optional<ConnectionMethod> effectivePublicMethod(NativeOwnership claim) {
        Optional<ConnectionMethod> requested = claim.requestedMethod()
                .filter(method -> method != ConnectionMethod.AUTO);
        if (requested.isPresent()) return requested;
        return claim.resolvedMethod().filter(method -> method != ConnectionMethod.AUTO);
    }

    private static NativeOwnership restrictSlotsToDomain(
            NativeOwnership claim,
            ConfiguredMethodPlan method) {
        List<NativeSlot> domainSlots = claim.slots().stream()
                .filter(slot -> method.domain().slots().contains(slot.index()))
                .toList();
        if (domainSlots.size() == claim.slots().size()) return claim;
        return new NativeOwnership(
                claim.match(),
                claim.source(),
                claim.requestedMethod(),
                claim.resolvedMethod(),
                domainSlots,
                claim.reason());
    }

    public enum Outcome {
        PASSTHROUGH,
        NATIVE_ONLY,
        CONSERVATIVE_NATIVE,
        FULL,
        COMPLEMENT
    }
}
