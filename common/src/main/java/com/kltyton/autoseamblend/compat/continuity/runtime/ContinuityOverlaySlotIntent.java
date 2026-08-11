package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import java.util.Objects;

/**
 * 中文：记录一个标准 overlay 槽位在原生规则中的占用意图。
 *
 * English: Describes the native occupancy intent of one standard-overlay slot.
 */
public enum ContinuityOverlaySlotIntent {
    /** Native evidence says AutoBlend may fill this slot. */
    FILLABLE,
    /** Native evidence provides a present sprite for this slot. */
    PRESENT,
    /** Native evidence protects this slot from completion. */
    PROTECTED;

    /**
     * 中文：把统一的原生槽位证据折叠为 overlay 补全意图。
     * English: Folds normalized native-slot evidence into an overlay completion intent.
     */
    public static ContinuityOverlaySlotIntent from(
            NativeSlotIntent evidence) {
        NativeSlotIntent intent = Objects.requireNonNull(evidence, "evidence");
        if (intent == NativeSlotIntent.PRESENT) {
            return PRESENT;
        }
        return intent.fillable() ? FILLABLE : PROTECTED;
    }
}
