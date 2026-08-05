package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.EngineAdapter;
import com.kltyton.autoseamblend.engine.ownership.NativeOwnership;
import com.kltyton.autoseamblend.engine.ownership.NativeRuleSource;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 中文：不可变注册表，以及按查询或文档进行的确定性选择；同一家族再按 engineId 平局。
 * English: Immutable registry plus deterministic per-query/document selection; engineId breaks
 * ties within one family.
 */
public record EngineRegistrySnapshot(List<EngineRegistration> registrations, List<EngineDiagnostic> diagnostics) {
    public EngineRegistrySnapshot {
        registrations = List.copyOf(registrations);
        diagnostics = List.copyOf(diagnostics);
    }

    public EngineSelection select(EngineSelectionRequest request) {
        List<EngineAdapter> ready = readyAdapters();
        if (ready.isEmpty()) {
            return new EngineSelection(EngineStatus.State.ENGINE_REQUIRED, Optional.empty(),
                    "ENGINE_REQUIRED", appendEngineRequired(diagnostics));
        }
        Map<String, EngineAdapter> byId = ready.stream().collect(Collectors.toUnmodifiableMap(
                adapter -> adapter.descriptor().engineId(), Function.identity()));
        java.util.ArrayList<SelectionClaim> claims = new java.util.ArrayList<>();
        request.documentClaim().ifPresent(document -> {
            EngineAdapter adapter = byId.get(document.engineId());
            if (adapter != null) {
                claims.add(new SelectionClaim(
                        adapter, document.sourcePriority(), document.packPriority(), true));
            }
        });
        request.ownershipClaims().stream()
                .filter(NativeOwnership::ownsQuery)
                .filter(claim -> claim.source().map(source -> byId.containsKey(source.engineId())).orElse(false))
                .forEach(claim -> {
                    NativeRuleSource source = claim.source().orElseThrow();
                    claims.add(new SelectionClaim(
                            byId.get(source.engineId()), source.sourcePriority(),
                            source.packPriority(), false));
                });
        Optional<SelectionClaim> owner = claims.stream()
                .sorted(Comparator.comparingInt(SelectionClaim::sourcePriority).reversed()
                        .thenComparing(Comparator.comparingInt(SelectionClaim::packPriority).reversed())
                        .thenComparingInt(claim -> claim.adapter().descriptor().family().stableOrder())
                        .thenComparing(claim -> claim.adapter().descriptor().engineId()))
                .findFirst();
        if (owner.isPresent()) {
            SelectionClaim selected = owner.orElseThrow();
            return selected(selected.adapter(), selected.document()
                    ? "document_owner_by_source_tier"
                    : "query_owner_by_source_tier");
        }

        EngineAdapter fallback = ready.stream()
                .min(Comparator.comparingInt((EngineAdapter adapter) ->
                                adapter.descriptor().family().stableOrder())
                        .thenComparing(adapter -> adapter.descriptor().engineId()))
                .orElseThrow();
        return selected(fallback, ready.size() == 1 ? "only_valid_engine" : "stable_family_order");
    }

    /**
     * 中文：返回按候选稳定顺序排列的完整适配器；Loader 不应复制此筛选逻辑。
     * English: Returns complete adapters in candidate stable order; loaders must not duplicate
     * this filtering logic.
     */
    public List<EngineAdapter> readyAdapters() {
        return registrations.stream()
                .filter(EngineRegistration::productSelectable)
                .flatMap(registration -> registration.adapter().stream())
                .toList();
    }

    public List<String> readyEngineIds() {
        return registrations.stream()
                .filter(EngineRegistration::productSelectable)
                .map(registration -> registration.descriptor().engineId())
                .toList();
    }

    public boolean engineRequired() {
        return readyAdapters().isEmpty();
    }

    public int stableFamilyOrder(String engineId) {
        return registrations.stream()
                .filter(registration -> registration.descriptor().engineId().equals(engineId))
                .mapToInt(registration -> registration.descriptor().family().stableOrder())
                .findFirst()
                .orElse(Integer.MAX_VALUE);
    }

    private EngineSelection selected(EngineAdapter adapter, String reason) {
        return new EngineSelection(EngineStatus.State.SELECTED, Optional.of(adapter), reason, diagnostics);
    }

    private static List<EngineDiagnostic> appendEngineRequired(List<EngineDiagnostic> source) {
        java.util.ArrayList<EngineDiagnostic> result = new java.util.ArrayList<>(source);
        result.add(EngineDiagnostic.error("ENGINE_REQUIRED", "No complete compatible texture engine is available"));
        return List.copyOf(result);
    }

    private record SelectionClaim(
            EngineAdapter adapter,
            int sourcePriority,
            int packPriority,
            boolean document) {}
}
