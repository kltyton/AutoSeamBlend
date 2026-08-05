package com.kltyton.autoseamblend.engine.routing.query;

import com.kltyton.autoseamblend.engine.EngineAdapter;
import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.NativeOwnership;
import com.kltyton.autoseamblend.engine.ownership.NativeRuleSource;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.engine.plan.MethodPlanResolution;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.engine.registry.EngineRegistrySnapshot;
import com.kltyton.autoseamblend.engine.registry.EngineSelectionRequest;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.query.QueryArbiter;
import com.kltyton.autoseamblend.selection.query.QueryResolution;
import com.kltyton.autoseamblend.selection.query.SelectionIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 中文：引擎查询的 common 编排器，统一负责观察收集、五级来源、引擎选择、AUTO 所有者与稳定回退。
 * English: Common engine-query orchestrator owning observation collection, five-tier provenance,
 * engine selection, AUTO ownership, and stable fallback.
 */
public final class EngineQueryRouting {
    private EngineQueryRouting() {}

    public static List<QueryObservation> observations(
            ConnectionQuery query,
            EngineQueryContext nativeContext,
            List<EngineAdapter> readyAdapters) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(nativeContext, "nativeContext");
        readyAdapters = List.copyOf(Objects.requireNonNull(readyAdapters, "readyAdapters"));
        ArrayList<QueryObservation> observations = new ArrayList<>();
        for (EngineAdapter adapter : readyAdapters) {
            QueryObservation observed = adapter.observe(query, nativeContext);
            if (!observed.slotClaims().isEmpty() || observed.conservativeBlocker().isPresent()) {
                observations.add(observed);
            }
        }
        return List.copyOf(observations);
    }

    public static Optional<ExecutionSelection> selectExecution(ExecutionInput input) {
        Objects.requireNonNull(input, "input");
        List<NativeOwnership> ordered = QueryArbiter.orderedContentClaims(
                input.observations(), input.engines());
        return input.engines()
                .select(new EngineSelectionRequest(Optional.empty(), ordered))
                .adapter()
                .map(adapter -> new ExecutionSelection(
                        adapter.descriptor(),
                        input.observations(),
                        ordered,
                        requestedMethod(ordered, input.explicit(), input.implicit())));
    }

    public static EngineRouteSelection resolveExact(ExactInput input) {
        Objects.requireNonNull(input, "input");
        QueryResolution resolution = QueryArbiter.resolve(
                input.generation(),
                input.reloadToken(),
                input.execution().engine(),
                input.query(),
                input.explicit(),
                input.implicit(),
                input.configExcluded(),
                input.execution().observations(),
                input.facts(),
                input.preparedMethod(),
                input.engines());
        Optional<NativeOwnership> winningClaim = resolution.orderedContentClaims().stream().findFirst();
        return new EngineRouteSelection(
                input.execution().engine(),
                provenance(resolution),
                winningClaim.map(NativeOwnership::slots).orElseGet(List::of),
                Optional.of(resolution),
                resolution.method().resolvedMethod());
    }

    public static Optional<EngineRouteSelection> summary(SummaryInput input) {
        Objects.requireNonNull(input, "input");
        if (input.engines().engineRequired()) {
            return Optional.empty();
        }
        ArrayList<NativeOwnership> claims = new ArrayList<>();
        int ordinal = 0;
        for (String engineId : input.engines().readyEngineIds()) {
            int currentOrdinal = ordinal;
            EngineDescriptor descriptor = descriptor(input.engines(), engineId);
            if (input.acceptedModelOwners().contains(descriptor.family())) {
                claims.add(conservative(
                        descriptor,
                        input.blockId(),
                        currentOrdinal,
                        "SUMMARY_ACCEPTED_MODEL_IDENTITY_UNAVAILABLE"));
            } else {
                Optional.ofNullable(input.unknownDiagnostics().get(engineId))
                        .ifPresent(diagnostic -> claims.add(conservative(
                                descriptor, input.blockId(), currentOrdinal, diagnostic)));
            }
            ordinal++;
        }
        if (claims.isEmpty()
                && input.configured().isEmpty()
                && (input.configExcluded() || !input.automaticDiscovery())) {
            return Optional.empty();
        }
        Optional<EngineAdapter> selected = input.engines()
                .select(new EngineSelectionRequest(Optional.empty(), claims))
                .adapter();
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        EngineDescriptor engine = selected.orElseThrow().descriptor();
        Optional<NativeRuleSource> claimSource = QueryArbiter
                .orderedContentClaims(
                        claims.stream()
                                .map(claim -> new QueryObservation(List.of(), Optional.of(claim)))
                                .toList(),
                        input.engines())
                .stream()
                .filter(claim -> claim.source()
                        .map(source -> source.engineId().equals(engine.engineId()))
                        .orElse(false))
                .findFirst()
                .flatMap(NativeOwnership::source);
        EngineRouteProvenance provenance = claimSource
                .map(EngineRouteProvenance::document)
                .or(() -> input.configured().map(intent ->
                        EngineRouteProvenance.config(intent.sourceTier(), intent.order())))
                .orElseGet(EngineRouteProvenance::stableFallback);
        ConnectionMethod method = input.configured()
                .map(SelectionIntent::method)
                .orElse(ConnectionMethod.AUTO);
        return Optional.of(new EngineRouteSelection(
                engine, provenance, List.of(), Optional.empty(), method));
    }

    public static Optional<EngineRouteSelection> fallback(EngineRegistrySnapshot engines) {
        Objects.requireNonNull(engines, "engines");
        return engines.select(EngineSelectionRequest.automatic())
                .adapter()
                .map(adapter -> new EngineRouteSelection(
                        adapter.descriptor(),
                        EngineRouteProvenance.stableFallback(),
                        List.of(),
                        Optional.empty(),
                        ConnectionMethod.AUTO));
    }

    public static <T> SelectionIntent explicitIntent(ConnectionRuleSet.CompiledSelector<T> selector) {
        Objects.requireNonNull(selector, "selector");
        boolean compatibility = selector.mode() == ConnectionRuleSet.ResourcePackMode.COMPATIBILITY;
        return new SelectionIntent(
                selector.identity(),
                selector.groupId(),
                selector.method(),
                compatibility ? SourceTier.CONFIG_COMPATIBILITY : SourceTier.CONFIG_NON_COMPATIBILITY,
                AutoBlendPolicy.fromCompatibility(compatibility),
                selector.order(),
                false);
    }

    public static SelectionIntent implicitIntent(String blockId) {
        requireText(blockId, "blockId");
        return new SelectionIntent(
                "discovery:" + blockId,
                blockId,
                ConnectionMethod.AUTO,
                SourceTier.CONFIG_COMPATIBILITY,
                AutoBlendPolicy.ALLOW_COMPLETION,
                Integer.MAX_VALUE,
                true);
    }

    private static ConnectionMethod requestedMethod(
            List<NativeOwnership> ordered,
            Optional<SelectionIntent> explicit,
            Optional<SelectionIntent> implicit) {
        return ordered.stream()
                .filter(claim -> claim.match() == NativeOwnership.Match.MATCH)
                .map(claim -> claim.requestedMethod().or(claim::resolvedMethod))
                .flatMap(Optional::stream)
                .findFirst()
                .or(() -> explicit.map(SelectionIntent::method))
                .or(() -> implicit.map(SelectionIntent::method))
                .orElse(ConnectionMethod.NONE);
    }

    private static EngineRouteProvenance provenance(QueryResolution resolution) {
        return resolution.orderedContentClaims().stream()
                .findFirst()
                .flatMap(NativeOwnership::source)
                .map(EngineRouteProvenance::document)
                .or(() -> resolution.selection().map(intent ->
                        EngineRouteProvenance.config(intent.sourceTier(), intent.order())))
                .orElseGet(EngineRouteProvenance::stableFallback);
    }

    private static NativeOwnership conservative(
            EngineDescriptor engine,
            String blockId,
            int ordinal,
            String diagnostic) {
        requireText(blockId, "blockId");
        requireText(diagnostic, "diagnostic");
        return new NativeOwnership(
                NativeOwnership.Match.CONSERVATIVE_UNKNOWN,
                Optional.of(new NativeRuleSource(
                        engine.engineId(),
                        SourceTier.NATIVE_AUTHOR,
                        Optional.empty(),
                        "native-engine:" + engine.engineId(),
                        "accepted-model:" + blockId,
                        0,
                        ordinal)),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                diagnostic);
    }

    private static EngineDescriptor descriptor(EngineRegistrySnapshot engines, String engineId) {
        return engines.readyAdapters().stream()
                .map(EngineAdapter::descriptor)
                .filter(descriptor -> descriptor.engineId().equals(engineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ready engine is missing its descriptor"));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    public record ExecutionInput(
            EngineRegistrySnapshot engines,
            List<QueryObservation> observations,
            Optional<SelectionIntent> explicit,
            Optional<SelectionIntent> implicit) {
        public ExecutionInput {
            Objects.requireNonNull(engines, "engines");
            observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
            explicit = Objects.requireNonNull(explicit, "explicit");
            implicit = Objects.requireNonNull(implicit, "implicit");
        }
    }

    public record ExecutionSelection(
            EngineDescriptor engine,
            List<QueryObservation> observations,
            List<NativeOwnership> orderedClaims,
            ConnectionMethod requestedMethod) {
        public ExecutionSelection {
            Objects.requireNonNull(engine, "engine");
            observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
            orderedClaims = List.copyOf(Objects.requireNonNull(orderedClaims, "orderedClaims"));
            Objects.requireNonNull(requestedMethod, "requestedMethod");
        }
    }

    public record ExactInput(
            long generation,
            String reloadToken,
            EngineRegistrySnapshot engines,
            ExecutionSelection execution,
            ConnectionQuery query,
            Optional<SelectionIntent> explicit,
            Optional<SelectionIntent> implicit,
            boolean configExcluded,
            InferenceFacts facts,
            Optional<MethodPlanResolution> preparedMethod) {
        public ExactInput {
            if (generation < 0) {
                throw new IllegalArgumentException("generation must be non-negative");
            }
            requireText(reloadToken, "reloadToken");
            Objects.requireNonNull(engines, "engines");
            Objects.requireNonNull(execution, "execution");
            Objects.requireNonNull(query, "query");
            if (query.requestedMethod() != execution.requestedMethod()) {
                throw new IllegalArgumentException(
                        "exact query method must match the selected execution request");
            }
            explicit = Objects.requireNonNull(explicit, "explicit");
            implicit = Objects.requireNonNull(implicit, "implicit");
            Objects.requireNonNull(facts, "facts");
            preparedMethod = Objects.requireNonNull(preparedMethod, "preparedMethod");
        }
    }

    public record SummaryInput(
            EngineRegistrySnapshot engines,
            String blockId,
            Set<EngineFamily> acceptedModelOwners,
            Map<String, String> unknownDiagnostics,
            Optional<SelectionIntent> configured,
            boolean configExcluded,
            boolean automaticDiscovery) {
        public SummaryInput {
            Objects.requireNonNull(engines, "engines");
            requireText(blockId, "blockId");
            acceptedModelOwners = Set.copyOf(
                    Objects.requireNonNull(acceptedModelOwners, "acceptedModelOwners"));
            unknownDiagnostics = Map.copyOf(
                    Objects.requireNonNull(unknownDiagnostics, "unknownDiagnostics"));
            configured = Objects.requireNonNull(configured, "configured");
        }
    }
}
