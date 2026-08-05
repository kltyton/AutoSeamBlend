package com.kltyton.autoseamblend.selection.query;

import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;

/** 中文：有序显式选择器或重载局部隐式发现意图。 / English: Ordered explicit selector or reload-local implicit discovery intent. */
public record SelectionIntent(
        String identity,
        String connectionGroup,
        ConnectionMethod method,
        SourceTier sourceTier,
        AutoBlendPolicy policy,
        int order,
        boolean implicit) {
    public SelectionIntent {
        requireText(identity, "identity");
        requireText(connectionGroup, "connectionGroup");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(sourceTier, "sourceTier");
        Objects.requireNonNull(policy, "policy");
        if (order < 0) throw new IllegalArgumentException("order must be non-negative");
        if (sourceTier == SourceTier.NATIVE_AUTHOR
                || sourceTier == SourceTier.MANAGED_COMPATIBILITY
                || sourceTier == SourceTier.MANAGED_NON_COMPATIBILITY) {
            throw new IllegalArgumentException("selection intents are config or implicit intents only");
        }
        if (sourceTier == SourceTier.CONFIG_COMPATIBILITY && !policy.allowsCompletion()) {
            throw new IllegalArgumentException("compatibility selector requires completion policy");
        }
        if (sourceTier == SourceTier.CONFIG_NON_COMPATIBILITY && policy.allowsCompletion()) {
            throw new IllegalArgumentException("non-compatibility selector requires native-exclusive policy");
        }
        if (implicit && (method != ConnectionMethod.AUTO
                || sourceTier != SourceTier.CONFIG_COMPATIBILITY
                || !policy.allowsCompletion())) {
            throw new IllegalArgumentException("implicit discovery is always auto with completion enabled");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
