package com.kltyton.autoseamblend.engine.plan;

import com.kltyton.autoseamblend.engine.query.ResolutionKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 中文：包含多个逐键具体计划的不可变代次快照。 / English: One immutable generation snapshot containing many concrete per-key plans. */
public record PlanSnapshot(
        long generation,
        Map<ResolutionKey, ResolutionPlan> plans,
        Map<AuthorRuleKey, AuthorExecutionView> authorViews,
        List<String> diagnostics) {
    public PlanSnapshot {
        if (generation < 0) throw new IllegalArgumentException("generation must be non-negative");
        plans = immutableCopy(plans);
        authorViews = immutableCopy(authorViews);
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        plans.forEach((key, plan) -> {
            if (key.generation() != generation) {
                throw new IllegalArgumentException("resolution key belongs to another plan generation");
            }
            if (!key.equals(plan.queryResolution().key())) {
                throw new IllegalArgumentException("plan map key and query resolution differ");
            }
            if (plan.methodResolution().method().identity().generation() != generation
                    || !key.reloadToken().equals(
                            plan.methodResolution().method().identity().reloadToken())
                    || !key.engineId().equals(plan.methodResolution().method().identity().engineId())) {
                throw new IllegalArgumentException("resolution method belongs to another snapshot identity");
            }
        });
        authorViews.forEach((key, view) -> {
            if (!key.equals(view.key())) {
                throw new IllegalArgumentException("author view belongs to another reload");
            }
            if (view.exactBindings().values().stream().anyMatch(binding ->
                    binding.method().identity().generation() != generation
                            || !view.reloadToken().equals(
                                    binding.method().identity().reloadToken())
                            || !key.engineId().equals(binding.method().identity().engineId()))) {
                throw new IllegalArgumentException("author method belongs to another snapshot identity");
            }
        });
    }

    public static PlanSnapshot empty(long generation) {
        return new PlanSnapshot(generation, Map.of(), Map.of(), List.of());
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(source, "source")));
    }
}
