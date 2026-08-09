package com.kltyton.autoseamblend.frontend.uilib.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorkbenchBackdropPolicyTest {
    @Test
    void workbenchKeepsVanillaBlurAndDarkenNotUilibGradient() {
        assertEquals(
                WorkbenchBackdropPolicy.Kind.VANILLA_BLUR_DARKEN,
                WorkbenchBackdropPolicy.selected());
    }
}
