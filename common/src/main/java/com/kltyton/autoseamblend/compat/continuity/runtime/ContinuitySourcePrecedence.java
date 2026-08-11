package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import java.util.Objects;

/**
 * 中文：集中 Continuity holder 的来源优先级排序规则。
 *
 * <p>English: Centralizes the source-precedence ordering used for Continuity holders.
 */
public final class ContinuitySourcePrecedence {
    private ContinuitySourcePrecedence() {}

    /**
     * 中文：返回按高优先级在前排序的比较结果。
     * English: Returns a comparator result that orders higher-precedence sources first.
     */
    public static int compare(SourceTier left, SourceTier right) {
        SourceTier leftTier = Objects.requireNonNull(left, "left");
        SourceTier rightTier = Objects.requireNonNull(right, "right");
        return Integer.compare(rightTier.priority(), leftTier.priority());
    }
}
