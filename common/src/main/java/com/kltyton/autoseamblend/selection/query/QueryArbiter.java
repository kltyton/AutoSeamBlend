package com.kltyton.autoseamblend.selection.query;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.NativeOwnership;
import com.kltyton.autoseamblend.engine.ownership.NativeRuleSource;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.engine.plan.CompletionPlan;
import com.kltyton.autoseamblend.engine.plan.MethodPlanResolution;
import com.kltyton.autoseamblend.engine.plan.PlanIdentity;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.engine.query.ResolutionKey;
import com.kltyton.autoseamblend.engine.registry.EngineRegistrySnapshot;
import com.kltyton.autoseamblend.inference.InferenceDecision;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.inference.InferencePolicy;
import com.kltyton.autoseamblend.selection.method.ConfiguredMethodPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 中文：唯一的五级查询与槽位仲裁器；每个观察结果已限定在精确查询范围内。 / English: Sole five-tier query and slot arbiter. Every observation is already exact-query scoped. */
public final class QueryArbiter {
    private QueryArbiter() {}

    public static QueryResolution resolve(
            long generation,
            String reloadToken,
            EngineDescriptor executingEngine,
            ConnectionQuery query,
            Optional<SelectionIntent> explicit,
            Optional<SelectionIntent> implicit,
            boolean configExcluded,
            List<QueryObservation> observations,
            InferenceFacts facts,
            Optional<MethodPlanResolution> preparedMethod,
            EngineRegistrySnapshot engines) {
        return resolve(
                generation,
                reloadToken,
                executingEngine,
                query,
                explicit,
                implicit,
                configExcluded,
                observations,
                facts,
                preparedMethod,
                engines,
                true);
    }

    /**
     * Resolves the render-time route without constructing human-readable diagnostics that no
     * renderer consumes. All ownership, method, policy, and completion decisions remain identical
     * to {@link #resolve}.
     */
    public static QueryResolution resolveRuntime(
            long generation,
            String reloadToken,
            EngineDescriptor executingEngine,
            ConnectionQuery query,
            Optional<SelectionIntent> explicit,
            Optional<SelectionIntent> implicit,
            boolean configExcluded,
            List<QueryObservation> observations,
            InferenceFacts facts,
            Optional<MethodPlanResolution> preparedMethod,
            EngineRegistrySnapshot engines) {
        return resolve(
                generation,
                reloadToken,
                executingEngine,
                query,
                explicit,
                implicit,
                configExcluded,
                observations,
                facts,
                preparedMethod,
                engines,
                false);
    }

    private static QueryResolution resolve(
            long generation,
            String reloadToken,
            EngineDescriptor executingEngine,
            ConnectionQuery query,
            Optional<SelectionIntent> explicit,
            Optional<SelectionIntent> implicit,
            boolean configExcluded,
            List<QueryObservation> observations,
            InferenceFacts facts,
            Optional<MethodPlanResolution> preparedMethod,
            EngineRegistrySnapshot engines,
            boolean includeDiagnostics) {
        if (generation < 0) throw new IllegalArgumentException("generation must be non-negative");
        requireText(reloadToken, "reloadToken");
        Objects.requireNonNull(executingEngine, "executingEngine");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(explicit, "explicit");
        Objects.requireNonNull(implicit, "implicit");
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        Objects.requireNonNull(facts, "facts");
        preparedMethod = Objects.requireNonNull(preparedMethod, "preparedMethod");
        Objects.requireNonNull(engines, "engines");

        Optional<SelectionIntent> selection = configExcluded
                ? Optional.empty()
                : (explicit.isPresent() ? explicit : implicit);
        List<NativeOwnership> orderedContentClaims =
                orderedContentClaims(observations, engines);
        ArrayList<NativeOwnership> exactClaimsBuilder = new ArrayList<>();
        ArrayList<NativeOwnership> nativeAuthorExactBuilder = new ArrayList<>();
        ArrayList<NativeOwnership> managedExactBuilder = new ArrayList<>();
        NativeOwnership methodOwnerValue = null;
        NativeOwnership policyOwnerValue = null;
        for (NativeOwnership claim : orderedContentClaims) {
            if (claim.match() != NativeOwnership.Match.MATCH) {
                continue;
            }
            exactClaimsBuilder.add(claim);
            NativeRuleSource source = claim.source().orElse(null);
            if (source != null) {
                if (source.tier() == SourceTier.NATIVE_AUTHOR) {
                    nativeAuthorExactBuilder.add(claim);
                } else if (source.tier() == SourceTier.MANAGED_COMPATIBILITY
                        || source.tier() == SourceTier.MANAGED_NON_COMPATIBILITY) {
                    managedExactBuilder.add(claim);
                }
                if (policyOwnerValue == null && source.strategyPolicy().isPresent()) {
                    policyOwnerValue = claim;
                }
            }
            if (methodOwnerValue == null && effectivePublicMethodValue(claim) != null) {
                methodOwnerValue = claim;
            }
        }
        List<NativeOwnership> exactClaims = List.copyOf(exactClaimsBuilder);
        List<NativeOwnership> nativeAuthorExact = List.copyOf(nativeAuthorExactBuilder);
        List<NativeOwnership> managedExact = List.copyOf(managedExactBuilder);
        Optional<QueryMethodOwner> methodOwner = methodOwnerValue != null
                ? Optional.of(methodOwner(methodOwnerValue))
                : selection.map(QueryArbiter::methodOwner);
        Optional<QueryPolicyOwner> policyOwner = policyOwnerValue != null
                ? Optional.of(policyOwner(policyOwnerValue))
                : selection.map(QueryArbiter::policyOwner);
        AutoBlendPolicy policy = policyOwner
                .map(QueryPolicyOwner::policy)
                .orElse(AutoBlendPolicy.NATIVE_EXCLUSIVE);
        boolean hasNativeAuthorExact = !nativeAuthorExact.isEmpty();
        Optional<NativeOwnership> conservativeBlocker = conservativeBlocker(
                orderedContentClaims,
                nativeAuthorExact,
                policy,
                hasNativeAuthorExact,
                engines);

        ConnectionMethod requested = requestedMethod(methodOwner, selection);
        ConnectionQuery effectiveQuery = new ConnectionQuery(
                query.blockId(), query.stateProperties(), query.face(), query.spriteId(), requested);
        ResolutionKey key = new ResolutionKey(
                generation,
                reloadToken,
                executingEngine.engineId(),
                methodOwner,
                policyOwner,
                effectiveQuery.blockId(),
                selection.map(SelectionIntent::connectionGroup).orElse(effectiveQuery.blockId()),
                effectiveQuery.stateProperties(),
                effectiveQuery.face(),
                effectiveQuery.spriteId(),
                requested);
        PlanIdentity queryPlanIdentity = new PlanIdentity(
                generation,
                reloadToken,
                executingEngine.engineId(),
                resolutionIdentity(key));
        MethodPlanResolution methodResolution = selectMethod(
                requested,
                facts,
                preparedMethod,
                queryPlanIdentity,
                generation,
                reloadToken,
                executingEngine.engineId());
        if (methodOwnerValue != null) {
            if (effectivePublicMethodValue(methodOwnerValue)
                    != methodResolution.method().resolvedMethod()) {
                throw new IllegalArgumentException(
                        "method owner claim and final concrete public method must agree");
            }
        }

        CompletionPlan completion = CompletionPlan.create(
                methodResolution.method(),
                policy,
                hasNativeAuthorExact,
                exactClaims,
                conservativeBlocker);

        List<String> diagnostics = includeDiagnostics
                ? diagnostics(
                        executingEngine,
                        methodResolution,
                        methodOwner,
                        policyOwner,
                        policy,
                        hasNativeAuthorExact,
                        completion,
                        configExcluded)
                : List.of();
        return new QueryResolution(
                key,
                effectiveQuery,
                selection,
                observations,
                orderedContentClaims,
                nativeAuthorExact,
                managedExact,
                conservativeBlocker,
                methodOwner,
                policyOwner,
                hasNativeAuthorExact,
                Optional.of(executingEngine),
                methodResolution,
                completion,
                diagnostics);
    }

    private static List<String> diagnostics(
            EngineDescriptor executingEngine,
            MethodPlanResolution methodResolution,
            Optional<QueryMethodOwner> methodOwner,
            Optional<QueryPolicyOwner> policyOwner,
            AutoBlendPolicy policy,
            boolean hasNativeAuthorExact,
            CompletionPlan completion,
            boolean configExcluded) {
        ArrayList<String> diagnostics = new ArrayList<>();
        diagnostics.add("source_order=native>managed_true>managed_false>config_true>config_false");
        diagnostics.add("engine=" + executingEngine.engineId());
        diagnostics.add("method=" + methodResolution.method().requestedMethod().serializedName()
                + "->" + methodResolution.method().resolvedMethod().serializedName());
        diagnostics.add("plan=" + methodResolution.method().identity().value());
        diagnostics.add("method_owner=" + ownerIdentity(methodOwner.map(QueryMethodOwner::provenance)));
        diagnostics.add("policy_owner=" + ownerIdentity(policyOwner.map(QueryPolicyOwner::provenance)));
        diagnostics.add("policy=" + policy.name());
        diagnostics.add("native_author_exact=" + hasNativeAuthorExact);
        diagnostics.add("completion=" + completion.outcome().name());
        if (configExcluded) diagnostics.add("config_and_implicit_excluded");
        diagnostics.addAll(methodResolution.method().reasons());
        return List.copyOf(diagnostics);
    }

    private static MethodPlanResolution selectMethod(
            ConnectionMethod requested,
            InferenceFacts facts,
            Optional<MethodPlanResolution> prepared,
            PlanIdentity queryIdentity,
            long generation,
            String reloadToken,
            String engineId) {
        if (prepared.isPresent()) {
            MethodPlanResolution resolution = prepared.orElseThrow();
            ConfiguredMethodPlan plan = resolution.method();
            if (plan.requestedMethod() != requested) {
                throw new IllegalArgumentException("prepared method requested value does not match method owner");
            }
            if (plan.identity().generation() != generation
                    || !plan.identity().reloadToken().equals(reloadToken)
                    || !plan.identity().engineId().equals(engineId)) {
                throw new IllegalArgumentException("prepared method belongs to another generation");
            }
            if (!resolution.facts().equals(facts)) {
                throw new IllegalArgumentException("prepared method facts differ from the query facts");
            }
            return resolution;
        }
        if (requested == ConnectionMethod.AUTO) {
            throw new IllegalStateException(
                    "auto query has no same-reload prepared method resolution; duplicate inference is forbidden");
        }
        InferenceDecision decision = InferencePolicy.decide(requested, facts);
        ConfiguredMethodPlan method = ConfiguredMethodPlan.fromDecision(queryIdentity, decision);
        return new MethodPlanResolution(facts, decision, method);
    }

    private static QueryMethodOwner methodOwner(NativeOwnership ownership) {
        return new QueryMethodOwner(
                IntentProvenance.nativeDocument(ownership.source().orElseThrow()),
                ownership.requestedMethod(),
                ownership.resolvedMethod());
    }

    public static Optional<ConnectionMethod> effectivePublicMethod(
            NativeOwnership ownership) {
        return Optional.ofNullable(effectivePublicMethodValue(ownership));
    }

    /** 中文：引擎选择与最终查询仲裁共享的稳定五级顺序。 / English: Stable five-tier ordering shared by engine selection and final query arbitration. */
    public static List<NativeOwnership> orderedContentClaims(
            List<QueryObservation> observations,
            EngineRegistrySnapshot engines) {
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        Objects.requireNonNull(engines, "engines");
        ArrayList<NativeOwnership> ordered = new ArrayList<>();
        for (QueryObservation observation : observations) {
            for (NativeOwnership ownership : observation.orderedOwnership()) {
                if (ownership.ownsQuery()) {
                    ordered.add(ownership);
                }
            }
        }
        ordered.sort(ownershipOrder(engines));
        return List.copyOf(ordered);
    }

    private static QueryMethodOwner methodOwner(SelectionIntent selection) {
        return new QueryMethodOwner(
                IntentProvenance.selection(selection),
                Optional.of(selection.method()),
                selection.method() == ConnectionMethod.AUTO
                        ? Optional.empty()
                        : Optional.of(selection.method()));
    }

    private static QueryPolicyOwner policyOwner(NativeOwnership ownership) {
        NativeRuleSource source = ownership.source().orElseThrow();
        return new QueryPolicyOwner(
                IntentProvenance.nativeDocument(source),
                source.strategyPolicy().orElseThrow(() ->
                        new IllegalStateException("extended native ownership is missing its AutoBlend strategy")));
    }

    private static QueryPolicyOwner policyOwner(SelectionIntent selection) {
        return new QueryPolicyOwner(IntentProvenance.selection(selection), selection.policy());
    }

    private static Comparator<NativeOwnership> ownershipOrder(EngineRegistrySnapshot engines) {
        return Comparator
                .comparingInt((NativeOwnership claim) -> claim.source()
                        .map(NativeRuleSource::sourcePriority).orElse(Integer.MAX_VALUE))
                .reversed()
                .thenComparing(Comparator.comparingInt((NativeOwnership claim) -> claim.source()
                        .map(NativeRuleSource::packPriority).orElse(Integer.MAX_VALUE)).reversed())
                .thenComparingInt(claim -> claim.source()
                        .map(source -> engines.stableFamilyOrder(source.engineId()))
                        .orElse(Integer.MIN_VALUE))
                .thenComparingInt(claim -> claim.source()
                        .map(NativeRuleSource::nativeOrdinal).orElse(Integer.MIN_VALUE));
    }

    private static Optional<NativeOwnership> conservativeBlocker(
            List<NativeOwnership> orderedContentClaims,
            List<NativeOwnership> nativeAuthorExact,
            AutoBlendPolicy policy,
            boolean hasNativeAuthorExact,
            EngineRegistrySnapshot engines) {
        for (NativeOwnership claim : orderedContentClaims) {
            if (claim.match() == NativeOwnership.Match.CONSERVATIVE_UNKNOWN
                    && !maskedByExclusiveHigherNative(
                            claim, nativeAuthorExact, policy, hasNativeAuthorExact, engines)) {
                return Optional.of(claim);
            }
        }
        return Optional.empty();
    }

    private static boolean maskedByExclusiveHigherNative(
            NativeOwnership unknown,
            List<NativeOwnership> nativeAuthorExact,
            AutoBlendPolicy policy,
            boolean hasNativeAuthorExact,
            EngineRegistrySnapshot engines) {
        if (policy != AutoBlendPolicy.NATIVE_EXCLUSIVE || !hasNativeAuthorExact) return false;
        if (unknown.source().isEmpty()) return false;
        for (NativeOwnership exact : nativeAuthorExact) {
            if (strictlyHigherPrecedence(exact, unknown, engines)) {
                return true;
            }
        }
        return false;
    }

    /** 中文：原生序号只对已接受结果排序，绝不允许同一生命周期的某个结果隐藏未知行为。 / English: Native ordinal orders accepted results but never lets one same-lifecycle result hide unknown behavior. */
    private static boolean strictlyHigherPrecedence(
            NativeOwnership higher,
            NativeOwnership lower,
            EngineRegistrySnapshot engines) {
        NativeRuleSource high = higher.source().orElseThrow();
        NativeRuleSource low = lower.source().orElseThrow();
        if (high.sourcePriority() != low.sourcePriority()) {
            return high.sourcePriority() > low.sourcePriority();
        }
        if (high.packPriority() != low.packPriority()) {
            return high.packPriority() > low.packPriority();
        }
        int highFamily = engines.stableFamilyOrder(high.engineId());
        int lowFamily = engines.stableFamilyOrder(low.engineId());
        return highFamily < lowFamily;
    }

    private static String resolutionIdentity(ResolutionKey key) {
        String methodIdentity = ownerIdentity(key.methodOwner().map(QueryMethodOwner::provenance));
        String policyIdentity = ownerIdentity(key.policyOwner().map(QueryPolicyOwner::provenance));
        return "query:" + methodIdentity + '|' + policyIdentity + '|' + key.targetId() + '|'
                + key.connectionGroup() + '|' + key.stateIdentity() + '|' + key.face().name() + '|'
                + key.spriteId() + '|' + key.requestedMethod().serializedName();
    }

    private static String ownerIdentity(Optional<IntentProvenance> provenance) {
        return provenance.map(IntentProvenance::identity).orElse("none");
    }

    private static ConnectionMethod effectivePublicMethodValue(NativeOwnership ownership) {
        ConnectionMethod requested = ownership.requestedMethod().orElse(null);
        if (requested != null && requested != ConnectionMethod.AUTO) {
            return requested;
        }
        ConnectionMethod resolved = ownership.resolvedMethod().orElse(null);
        return resolved == null || resolved == ConnectionMethod.AUTO ? null : resolved;
    }

    private static ConnectionMethod requestedMethod(
            Optional<QueryMethodOwner> methodOwner,
            Optional<SelectionIntent> selection) {
        if (methodOwner.isPresent()) {
            QueryMethodOwner owner = methodOwner.orElseThrow();
            ConnectionMethod requested = owner.requestedMethod().orElse(null);
            if (requested != null) {
                return requested;
            }
            ConnectionMethod resolved = owner.resolvedMethod().orElse(null);
            if (resolved != null) {
                return resolved;
            }
        }
        return selection.map(SelectionIntent::method).orElse(ConnectionMethod.NONE);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
