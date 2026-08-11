package com.kltyton.autoseamblend.runtime.overlay;

import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.query.IntentProvenance;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：overlay 供体和接收体共享的五级来源、包、顺序与视觉事实优先级。
 * English: Shared five-tier provenance, pack, order, and visual-fact priority for overlay donors
 * and receivers.
 */
public record OverlayCandidatePriority(
        SourceTier sourceTier,
        int packPriority,
        int order,
        int dominance,
        long visualSignature)
        implements Comparable<OverlayCandidatePriority> {
    private static final Comparator<OverlayCandidatePriority> ORDER =
            Comparator.comparingInt((OverlayCandidatePriority value) ->
                            value.sourceTier().priority())
                    .thenComparingInt(OverlayCandidatePriority::packPriority)
                    .thenComparing(
                            Comparator.comparingInt(OverlayCandidatePriority::order).reversed())
                    .thenComparingInt(OverlayCandidatePriority::dominance)
                    .thenComparing(
                            OverlayCandidatePriority::visualSignature,
                            Long::compareUnsigned);

    public OverlayCandidatePriority {
        Objects.requireNonNull(sourceTier, "sourceTier");
        if (order < 0) {
            throw new IllegalArgumentException("order must be non-negative");
        }
    }

    @Override
    public int compareTo(OverlayCandidatePriority other) {
        return ORDER.compare(this, Objects.requireNonNull(other, "other"));
    }

    /** 中文：只有严格更高优先级的供体可以覆盖接收体。 / English: Only a strictly higher-priority donor may paint over a receiver. */
    public boolean winsOver(OverlayCandidatePriority receiver) {
        return compareTo(receiver) > 0;
    }

    /**
     * 中文：从同一套配置选择器与可选精确查询来源构造统一优先级；配置来源没有资源包优先级，
     * `Integer.MIN_VALUE` 只是来源 DTO 的非原生哨兵，绝不能参与供体比较。
     * English: Builds one priority from the shared configured selector and optional exact-query
     * provenance. Config sources have no resource-pack priority; the `Integer.MIN_VALUE` stored by
     * non-native provenance is only a DTO sentinel and must never enter donor arbitration.
     */
    public static <T> OverlayCandidatePriority from(
            ConnectionRuleSet<T> rules,
            T target,
            Optional<IntentProvenance> provenance,
            int dominance,
            long visualSignature) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(target, "target");
        provenance = Objects.requireNonNull(provenance, "provenance");
        Optional<ConnectionRuleSet.CompiledSelector<T>> configured =
                rules.configuredSelector(target);
        SourceTier tier = configured
                .map(selector -> selector.mode()
                        == ConnectionRuleSet.ResourcePackMode.COMPATIBILITY
                                ? SourceTier.CONFIG_COMPATIBILITY
                                : SourceTier.CONFIG_NON_COMPATIBILITY)
                .orElse(SourceTier.CONFIG_NON_COMPATIBILITY);
        int packPriority = 0;
        int order = configured
                .map(ConnectionRuleSet.CompiledSelector::order)
                .orElse(Integer.MAX_VALUE);
        if (provenance.isPresent()) {
            IntentProvenance exact = provenance.orElseThrow();
            if (exact.kind() == IntentProvenance.Kind.NATIVE_DOCUMENT) {
                tier = exact.tier();
                packPriority = exact.packPriority();
                order = exact.nativeOrdinal();
            } else if (exact.kind() == IntentProvenance.Kind.CONFIG_SELECTOR) {
                tier = exact.tier();
            }
            // 中文：隐式发现是没有显式接收体所有者时的稳定回退，不能以 compatibility
            // 来源压过显式 non-compatibility 供体；这与 NeoForge 已验收的 STABLE_FALLBACK
            // 仲裁一致。
            // English: Implicit discovery is the stable fallback when no explicit receiver owner
            // exists. It must not outrank an explicit non-compatibility donor as a compatibility
            // source, matching the accepted NeoForge STABLE_FALLBACK arbitration.
        }
        return new OverlayCandidatePriority(
                tier, packPriority, order, dominance, visualSignature);
    }
}
