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
                : explicit.or(() -> implicit);
        List<NativeOwnership> orderedContentClaims =
                orderedContentClaims(observations, engines);
        List<NativeOwnership> exactClaims = orderedContentClaims.stream()
                .filter(claim -> claim.match() == NativeOwnership.Match.MATCH)
                .toList();
        List<NativeOwnership> nativeAuthorExact = exactClaims.stream()
                .filter(claim -> claim.source()
                        .map(source -> source.tier() == SourceTier.NATIVE_AUTHOR)
                        .orElse(false))
                .toList();
        List<NativeOwnership> managedExact = exactClaims.stream()
                .filter(claim -> claim.source()
                        .map(source -> source.tier() == SourceTier.MANAGED_COMPATIBILITY
                                || source.tier() == SourceTier.MANAGED_NON_COMPATIBILITY)
                        .orElse(false))
                .toList();
        Optional<NativeOwnership> methodOwnerClaim = exactClaims.stream()
                .filter(claim -> effectivePublicMethod(claim).isPresent())
                .findFirst();
        Optional<QueryMethodOwner> methodOwner = methodOwnerClaim
                .map(QueryArbiter::methodOwner)
                .or(() -> selection.map(QueryArbiter::methodOwner));
        Optional<QueryPolicyOwner> policyOwner = exactClaims.stream()
                .filter(claim -> claim.source()
                        .flatMap(NativeRuleSource::strategyPolicy)
                        .isPresent())
                .findFirst()
                .map(QueryArbiter::policyOwner)
                .or(() -> selection.map(QueryArbiter::policyOwner));
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

        ConnectionMethod requested = methodOwner
                .flatMap(QueryMethodOwner::requestedMethod)
                .or(() -> methodOwner.flatMap(QueryMethodOwner::resolvedMethod))
                .or(() -> selection.map(SelectionIntent::method))
                .orElse(ConnectionMethod.NONE);
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
        methodOwnerClaim.ifPresent(claim -> {
            if (effectivePublicMethod(claim).orElseThrow()
                    != methodResolution.method().resolvedMethod()) {
                throw new IllegalArgumentException(
                        "method owner claim and final concrete public method must agree");
            }
        });

        CompletionPlan completion = CompletionPlan.create(
                methodResolution.method(),
                policy,
                hasNativeAuthorExact,
                exactClaims,
                conservativeBlocker);

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
        Optional<ConnectionMethod> requested = ownership.requestedMethod()
                .filter(method -> method != ConnectionMethod.AUTO);
        if (requested.isPresent()) return requested;
        return ownership.resolvedMethod().filter(method -> method != ConnectionMethod.AUTO);
    }

    /** 中文：引擎选择与最终查询仲裁共享的稳定五级顺序。 / English: Stable five-tier ordering shared by engine selection and final query arbitration. */
    public static List<NativeOwnership> orderedContentClaims(
            List<QueryObservation> observations,
            EngineRegistrySnapshot engines) {
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        Objects.requireNonNull(engines, "engines");
        return observations.stream()
                .flatMap(observation -> observation.orderedOwnership().stream())
                .filter(NativeOwnership::ownsQuery)
                .sorted(ownershipOrder(engines))
                .toList();
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
        return orderedContentClaims.stream()
                .filter(claim -> claim.match() == NativeOwnership.Match.CONSERVATIVE_UNKNOWN)
                .filter(unknown -> !maskedByExclusiveHigherNative(
                        unknown, nativeAuthorExact, policy, hasNativeAuthorExact, engines))
                .findFirst();
    }

    private static boolean maskedByExclusiveHigherNative(
            NativeOwnership unknown,
            List<NativeOwnership> nativeAuthorExact,
            AutoBlendPolicy policy,
            boolean hasNativeAuthorExact,
            EngineRegistrySnapshot engines) {
        if (policy != AutoBlendPolicy.NATIVE_EXCLUSIVE || !hasNativeAuthorExact) return false;
        if (unknown.source().isEmpty()) return false;
        return nativeAuthorExact.stream().anyMatch(exact ->
                strictlyHigherPrecedence(exact, unknown, engines));
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

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
