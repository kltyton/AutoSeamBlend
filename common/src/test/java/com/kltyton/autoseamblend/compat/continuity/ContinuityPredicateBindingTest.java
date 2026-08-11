package com.kltyton.autoseamblend.compat.continuity;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityPredicateBinding;
import org.junit.jupiter.api.Test;

class ContinuityPredicateBindingTest {
    private static final Object ORIGIN_APPEARANCE = new Object();
    private static final Object ORIGIN_STATE = new Object();
    private static final Object OTHER_APPEARANCE = new Object();
    private static final Object OTHER_STATE = new Object();

    @Test
    void neighborConnectionBindsRealStateNotAppearance() {
        assertSame(
                OTHER_STATE,
                ContinuityPredicateBinding.neighborState(
                        OTHER_APPEARANCE,
                        OTHER_STATE));
        assertNotSame(
                OTHER_APPEARANCE,
                ContinuityPredicateBinding.neighborState(
                        OTHER_APPEARANCE,
                        OTHER_STATE));
    }

    @Test
    void originConnectionBindsRealStateNotAppearance() {
        assertSame(
                ORIGIN_STATE,
                ContinuityPredicateBinding.originState(
                        ORIGIN_APPEARANCE,
                        ORIGIN_STATE));
        assertNotSame(
                ORIGIN_APPEARANCE,
                ContinuityPredicateBinding.originState(
                        ORIGIN_APPEARANCE,
                        ORIGIN_STATE));
    }
}
