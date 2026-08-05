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
import java.util.function.Predicate;

/** 中文：一个 generation 的有效原生作者规则快照。 / English: Effective native-author rule snapshot for one generation. */
public record NativeRuleSnapshot(
        long generation,
        Map<RuleTargetKey, List<NativeRule>> rules,
        List<String> diagnostics) {
    public NativeRuleSnapshot {
        if (generation < 0) {
            throw new IllegalArgumentException(
                    "generation must be non-negative");
        }
        LinkedHashMap<RuleTargetKey, List<NativeRule>> copy =
                new LinkedHashMap<>();
        Objects.requireNonNull(rules, "rules").forEach(
                (key, values) -> copy.put(key, List.copyOf(values)));
        rules = Collections.unmodifiableMap(copy);
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    /**
     * 中文：按资源包优先级与捕获顺序稳定排序，只保留 Loader 已确认有效的文档，再按文档身份去重和分组。
     * English: Stably orders by pack priority and capture order, retains Loader-confirmed effective documents, then deduplicates and groups by document identity.
     */
    public static NativeRuleSnapshot create(
            long generation,
            List<NativeRule> candidates,
            Predicate<NativeRule> effectiveDocument,
            List<String> diagnostics) {
        ArrayList<NativeRule> ordered =
                new ArrayList<>(Objects.requireNonNull(candidates, "candidates"));
        ordered.sort(Comparator
                .comparingInt(NativeRule::packPriority)
                .thenComparingInt(NativeRule::order));
        Predicate<NativeRule> effective =
                Objects.requireNonNull(effectiveDocument, "effectiveDocument");
        LinkedHashMap<DocumentKey, NativeRule> documents =
                new LinkedHashMap<>();
        for (NativeRule candidate : ordered) {
            if (effective.test(candidate)) {
                documents.put(
                        new DocumentKey(
                                candidate.family(),
                                candidate.targetBlockId(),
                                candidate.resourceId()),
                        candidate);
            }
        }
        LinkedHashMap<RuleTargetKey, List<NativeRule>> grouped =
                new LinkedHashMap<>();
        for (NativeRule candidate : documents.values()) {
            grouped.computeIfAbsent(
                            new RuleTargetKey(
                                    candidate.family(),
                                    candidate.targetBlockId()),
                            ignored -> new ArrayList<>())
                    .add(candidate);
        }
        grouped.replaceAll((key, values) -> List.copyOf(values));
        return new NativeRuleSnapshot(
                generation,
                grouped,
                diagnostics);
    }

    public static NativeRuleSnapshot empty() {
        return empty(0);
    }

    public static NativeRuleSnapshot empty(long generation) {
        return new NativeRuleSnapshot(
                generation,
                Map.of(),
                List.of());
    }

    public Optional<NativeRule> rule(
            EngineFamily family,
            String targetBlockId) {
        return rules(family, targetBlockId).stream()
                .max(Comparator
                        .comparingInt(NativeRule::packPriority)
                        .thenComparingInt(NativeRule::order));
    }

    /** 中文：保留同一家族与目标的全部有效扩展文档。 / English: Retains every effective extension document for one family and target. */
    public List<NativeRule> rules(
            EngineFamily family,
            String targetBlockId) {
        return rules.getOrDefault(
                new RuleTargetKey(family, targetBlockId),
                List.of());
    }

    private record DocumentKey(
            EngineFamily family,
            String targetBlockId,
            String resourceId) {
        private DocumentKey {
            Objects.requireNonNull(family, "family");
            if (targetBlockId == null
                    || targetBlockId.isBlank()
                    || resourceId == null
                    || resourceId.isBlank()) {
                throw new IllegalArgumentException(
                        "invalid native document key");
            }
        }
    }
}
