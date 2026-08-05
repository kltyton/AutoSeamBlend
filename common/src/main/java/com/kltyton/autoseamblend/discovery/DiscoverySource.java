package com.kltyton.autoseamblend.discovery;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 中文：每个运行时代次都会保留的不可变原始模型检查输入。 / English: Immutable raw model-inspection input retained with every runtime generation. */
public record DiscoverySource(
        List<FaceFacts> facts,
        List<DiscoveryDiagnostic> diagnostics,
        Set<String> incompleteTargets) {
    public DiscoverySource {
        facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        LinkedHashSet<String> orderedIncomplete = new LinkedHashSet<>();
        Objects.requireNonNull(incompleteTargets, "incompleteTargets").stream()
                .sorted(Comparator.naturalOrder())
                .forEach(orderedIncomplete::add);
        incompleteTargets = Collections.unmodifiableSet(orderedIncomplete);
    }

    public static DiscoverySource empty() {
        return new DiscoverySource(List.of(), List.of(), Set.of());
    }
}
