package com.kltyton.autoseamblend.reload.rule;

import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 中文：一个 generation 的有序 Managed 规则与文档快照。 / English: Ordered Managed rule and document snapshot for one generation. */
public record ManagedRuleSnapshot(
        long generation,
        int packPriority,
        Map<RuleTargetKey, List<ManagedRule>> rules,
        List<ManagedRuleDocument> documents,
        List<String> diagnostics) {
    public ManagedRuleSnapshot {
        if (generation < 0) {
            throw new IllegalArgumentException(
                    "Managed generation must be non-negative");
        }
        if (packPriority < 0) {
            throw new IllegalArgumentException(
                    "Managed pack priority must be non-negative");
        }
        LinkedHashMap<RuleTargetKey, List<ManagedRule>> copy =
                new LinkedHashMap<>();
        Objects.requireNonNull(rules, "rules").forEach(
                (key, values) -> copy.put(key, List.copyOf(values)));
        rules = Collections.unmodifiableMap(copy);
        documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public static ManagedRuleSnapshot create(
            long generation,
            int packPriority,
            List<ManagedRule> orderedRules,
            List<ManagedRuleDocument> documents,
            List<String> diagnostics) {
        ArrayList<ManagedRule> stableRules =
                new ArrayList<>(Objects.requireNonNull(orderedRules, "orderedRules"));
        stableRules.sort(Comparator.comparingInt(ManagedRule::order));
        LinkedHashMap<RuleTargetKey, List<ManagedRule>> grouped = new LinkedHashMap<>();
        for (ManagedRule rule : stableRules) {
            grouped.computeIfAbsent(
                            new RuleTargetKey(rule.family(), rule.targetBlockId()),
                            ignored -> new ArrayList<>())
                    .add(rule);
        }
        grouped.replaceAll((key, values) -> List.copyOf(values));
        ArrayList<ManagedRuleDocument> stableDocuments =
                new ArrayList<>(Objects.requireNonNull(documents, "documents"));
        stableDocuments.sort(Comparator.comparingInt(ManagedRuleDocument::order));
        return new ManagedRuleSnapshot(
                generation,
                packPriority,
                grouped,
                stableDocuments,
                diagnostics);
    }

    public static ManagedRuleSnapshot empty() {
        return empty(0);
    }

    public static ManagedRuleSnapshot empty(long generation) {
        return new ManagedRuleSnapshot(
                generation,
                0,
                Map.of(),
                List.of(),
                List.of());
    }

    public Optional<ManagedRule> rule(
            EngineFamily family,
            String targetBlockId) {
        return rules(family, targetBlockId).stream()
                .min(Comparator.comparingInt(ManagedRule::order));
    }

    /** 中文：保留同一家族与目标的全部 Managed 文档。 / English: Retains every Managed document for one family and target. */
    public List<ManagedRule> rules(
            EngineFamily family,
            String targetBlockId) {
        return rules.getOrDefault(
                new RuleTargetKey(family, targetBlockId),
                List.of());
    }
}
