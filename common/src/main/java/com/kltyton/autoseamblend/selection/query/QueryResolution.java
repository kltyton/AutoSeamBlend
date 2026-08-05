package com.kltyton.autoseamblend.selection.query;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.ownership.NativeOwnership;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.engine.plan.CompletionPlan;
import com.kltyton.autoseamblend.engine.plan.MethodPlanResolution;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.engine.query.ResolutionKey;
import com.kltyton.autoseamblend.selection.method.ConfiguredMethodPlan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 中文：一个精确状态、面和精灵查询的完整不可变答案。 / English: Complete immutable answer for one exact state/face/sprite query. */
public record QueryResolution(
        ResolutionKey key,
        ConnectionQuery query,
        Optional<SelectionIntent> selection,
        List<QueryObservation> observations,
        List<NativeOwnership> orderedContentClaims,
        List<NativeOwnership> nativeAuthorExactClaims,
        List<NativeOwnership> managedExactClaims,
        Optional<NativeOwnership> conservativeBlocker,
        Optional<QueryMethodOwner> methodOwner,
        Optional<QueryPolicyOwner> policyOwner,
        boolean hasAcceptedNativeAuthorExactMatch,
        Optional<EngineDescriptor> selectedEngine,
        MethodPlanResolution methodResolution,
        CompletionPlan completion,
        List<String> diagnostics) {
    public QueryResolution {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(query, "query");
        selection = Objects.requireNonNull(selection, "selection");
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        orderedContentClaims = List.copyOf(Objects.requireNonNull(orderedContentClaims, "orderedContentClaims"));
        nativeAuthorExactClaims = List.copyOf(
                Objects.requireNonNull(nativeAuthorExactClaims, "nativeAuthorExactClaims"));
        managedExactClaims = List.copyOf(Objects.requireNonNull(managedExactClaims, "managedExactClaims"));
        conservativeBlocker = Objects.requireNonNull(conservativeBlocker, "conservativeBlocker");
        methodOwner = Objects.requireNonNull(methodOwner, "methodOwner");
        policyOwner = Objects.requireNonNull(policyOwner, "policyOwner");
        selectedEngine = Objects.requireNonNull(selectedEngine, "selectedEngine");
        Objects.requireNonNull(methodResolution, "methodResolution");
        Objects.requireNonNull(completion, "completion");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (!query.equals(key.query())) {
            throw new IllegalArgumentException("query resolution and resolution key must agree");
        }
        if (!methodOwner.equals(key.methodOwner()) || !policyOwner.equals(key.policyOwner())) {
            throw new IllegalArgumentException("structured provenance and resolution key must agree");
        }
        if (completion.method() != methodResolution.method()) {
            throw new IllegalArgumentException("query resolution and completion must share one method plan instance");
        }
        ConfiguredMethodPlan method = methodResolution.method();
        if (!method.identity().reloadToken().equals(key.reloadToken())
                || method.identity().generation() != key.generation()
                || !method.identity().engineId().equals(key.engineId())) {
            throw new IllegalArgumentException("method plan identity and resolution key generation must agree");
        }
        if (hasAcceptedNativeAuthorExactMatch != !nativeAuthorExactClaims.isEmpty()
                || hasAcceptedNativeAuthorExactMatch != completion.hasAcceptedNativeAuthorExactMatch()) {
            throw new IllegalArgumentException("native-author exact-match fact must be explicit and consistent");
        }
        if (nativeAuthorExactClaims.stream().anyMatch(claim -> claim.match() != NativeOwnership.Match.MATCH
                || claim.source().map(source -> source.tier() != SourceTier.NATIVE_AUTHOR).orElse(true))) {
            throw new IllegalArgumentException("native-author claims must be accepted tier-one exact matches");
        }
        if (managedExactClaims.stream().anyMatch(claim -> claim.match() != NativeOwnership.Match.MATCH
                || claim.source().map(source -> source.tier() != SourceTier.MANAGED_COMPATIBILITY
                        && source.tier() != SourceTier.MANAGED_NON_COMPATIBILITY).orElse(true))) {
            throw new IllegalArgumentException("managed claims must be accepted tier-two or tier-three matches");
        }
        List<NativeOwnership> finalOrderedContentClaims = orderedContentClaims;
        conservativeBlocker.ifPresent(blocker -> {
            if (!finalOrderedContentClaims.contains(blocker)
                    || blocker.match() != NativeOwnership.Match.CONSERVATIVE_UNKNOWN) {
                throw new IllegalArgumentException("conservative blocker must be an observed unknown content claim");
            }
        });
    }

    public ConfiguredMethodPlan method() {
        return methodResolution.method();
    }
}
