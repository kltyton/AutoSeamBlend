package com.kltyton.autoseamblend.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 中文：完整且不可变的发现代次，可用于原子发布。 / English: Complete immutable discovery generation, suitable for atomic publication. */
public record DiscoverySnapshot(
        long generation,
        Map<String, DiscoveryCandidate> candidates,
        List<DiscoveryDiagnostic> diagnostics) {
    public DiscoverySnapshot {
        if (generation < 0) throw new IllegalArgumentException("generation must be non-negative");
        LinkedHashMap<String, DiscoveryCandidate> ordered = new LinkedHashMap<>();
        Objects.requireNonNull(candidates, "candidates").entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        candidates = Collections.unmodifiableMap(ordered);

        ArrayList<DiscoveryDiagnostic> orderedDiagnostics =
                new ArrayList<>(Objects.requireNonNull(diagnostics, "diagnostics"));
        orderedDiagnostics.sort(Comparator
                .comparing(DiscoveryDiagnostic::targetId)
                .thenComparing(DiscoveryDiagnostic::code)
                .thenComparing(DiscoveryDiagnostic::detail));
        diagnostics = List.copyOf(orderedDiagnostics);
    }

    public static DiscoverySnapshot empty() {
        return new DiscoverySnapshot(0, Map.of(), List.of());
    }

    public Optional<DiscoveryCandidate> candidate(String targetId) {
        return Optional.ofNullable(candidates.get(targetId));
    }
}
