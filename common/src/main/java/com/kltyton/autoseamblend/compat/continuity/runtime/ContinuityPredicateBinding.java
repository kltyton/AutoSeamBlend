package com.kltyton.autoseamblend.compat.continuity.runtime;

/**
 * 中文：NeoContinuity ConnectionPredicate 契约的纯逻辑选择；同 ID 连接必须按真实
 * state/block 绑定，外观状态仅由引擎用于外观比较。
 *
 * English:
 * Pure-logic selection for the NeoContinuity ConnectionPredicate contract; the same-ID
 * connection must bind the real state/block, while appearance states are only for engine
 * appearance comparison.
 */
public final class ContinuityPredicateBinding {
    private ContinuityPredicateBinding() {}

    /**
     * 中文：shouldConnect(level, originAppearance, originState, ...) 中同 ID 连接使用真实
     * originState，而不是 originAppearance。
     *
     * English: The same-ID connection binds the real originState slot, not
     * originAppearance.
     */
    public static <T> T originState(
            T originAppearance,
            T originState) {
        return originState;
    }

    /**
     * 中文：shouldConnect(..., otherAppearance, otherState, ...) 中同 ID 连接使用真实
     * otherState 绑定邻居，而不是 otherAppearance。
     *
     * English:
     * The same-ID connection binds the neighbor by the real otherState slot, not
     * otherAppearance.
     */
    public static <T> T neighborState(
            T otherAppearance,
            T otherState) {
        return otherState;
    }
}
