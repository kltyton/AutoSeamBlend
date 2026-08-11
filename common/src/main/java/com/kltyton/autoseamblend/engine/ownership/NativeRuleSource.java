package com.kltyton.autoseamblend.engine.ownership;

import java.util.Objects;
import java.util.Optional;

/** 中文：精确的已接受文档来源及其资源包优先级。 / English: Exact accepted document source and its resource-pack precedence. */
public record NativeRuleSource(
        String engineId,
        SourceTier tier,
        Optional<AutoBlendPolicy> strategyPolicy,
        String packId,
        String resourceId,
        int packPriority,
        int nativeOrdinal) {
    public NativeRuleSource {
        requireText(engineId, "engineId");
        if (tier == null) throw new IllegalArgumentException("tier must not be null");
        if (tier == SourceTier.CONFIG_COMPATIBILITY || tier == SourceTier.CONFIG_NON_COMPATIBILITY) {
            throw new IllegalArgumentException("accepted native documents cannot have config provenance");
        }
        strategyPolicy = Objects.requireNonNull(strategyPolicy, "strategyPolicy");
        if (tier == SourceTier.MANAGED_COMPATIBILITY
                && strategyPolicy.filter(AutoBlendPolicy::allowsCompletion).isEmpty()) {
            throw new IllegalArgumentException("compatibility provenance requires completion policy");
        }
        if (tier == SourceTier.MANAGED_NON_COMPATIBILITY
                && strategyPolicy.filter(policy -> !policy.allowsCompletion()).isEmpty()) {
            throw new IllegalArgumentException("non-compatibility provenance requires native-exclusive policy");
        }
        /*
         * 中文：原生作者文档仍属于最高来源等级，但它可以通过 AutoSeamBlend
         * 扩展显式选择补齐或原生独占策略；没有扩展的作者内容继续保持空策略。
         *
         * English:
         * A native author document remains in the highest source tier, while an
         * explicit AutoSeamBlend extension may select completion or native
         * exclusivity. Unextended author content keeps an absent policy.
         */
        requireText(packId, "packId");
        requireText(resourceId, "resourceId");
        if (nativeOrdinal < 0) throw new IllegalArgumentException("nativeOrdinal must be non-negative");
    }

    /** 中文：五级产品优先级：原生、Managed true/false、配置 true/false。 / English: Five-level product priority: native, managed true/false, config true/false. */
    public int sourcePriority() {
        return tier.priority();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
