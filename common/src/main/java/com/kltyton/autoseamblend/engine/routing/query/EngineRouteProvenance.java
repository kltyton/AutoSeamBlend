package com.kltyton.autoseamblend.engine.routing.query;

import com.kltyton.autoseamblend.engine.ownership.NativeRuleSource;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：路由胜出来源的不可变投影，供运行时、预览和 overlay 使用同一个优先级事实。
 * English: Immutable winning-source projection shared by runtime, preview, and overlay priority.
 */
public record EngineRouteProvenance(
        EngineRouteSource source,
        SourceTier sourceTier,
        int packPriority,
        int order,
        Optional<NativeRuleSource> documentSource) {
    public EngineRouteProvenance {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceTier, "sourceTier");
        documentSource = Objects.requireNonNull(documentSource, "documentSource");
        if (order < 0) {
            throw new IllegalArgumentException("order must be non-negative");
        }
        if (source.priorityTier() != sourceTier) {
            throw new IllegalArgumentException("route source and source tier must agree");
        }
        if (documentSource.isPresent() != !source.configOrFallback()) {
            throw new IllegalArgumentException("only native or Managed routes have document provenance");
        }
        documentSource.ifPresent(document -> {
            if (document.tier() != sourceTier
                    || document.packPriority() != packPriority
                    || document.nativeOrdinal() != order) {
                throw new IllegalArgumentException("document provenance must match the route priority projection");
            }
        });
    }

    public static EngineRouteProvenance document(NativeRuleSource source) {
        Objects.requireNonNull(source, "source");
        return new EngineRouteProvenance(
                EngineRouteSource.from(source.tier()),
                source.tier(),
                source.packPriority(),
                source.nativeOrdinal(),
                Optional.of(source));
    }

    public static EngineRouteProvenance config(SourceTier tier, int order) {
        if (tier != SourceTier.CONFIG_COMPATIBILITY
                && tier != SourceTier.CONFIG_NON_COMPATIBILITY) {
            throw new IllegalArgumentException("config provenance requires a config source tier");
        }
        return new EngineRouteProvenance(
                EngineRouteSource.from(tier), tier, 0, order, Optional.empty());
    }

    public static EngineRouteProvenance stableFallback() {
        return new EngineRouteProvenance(
                EngineRouteSource.STABLE_FALLBACK,
                SourceTier.CONFIG_NON_COMPATIBILITY,
                0,
                Integer.MAX_VALUE,
                Optional.empty());
    }
}
