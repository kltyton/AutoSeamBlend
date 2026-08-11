package com.kltyton.autoseamblend.frontend.uilib.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ButtonSpriteStateTest {
    @Test
    void disabledSpriteWinsOverEverything() {
        assertEquals(
                1,
                ButtonSpriteState.spriteIndex(false, true));
        assertEquals(
                1,
                ButtonSpriteState.spriteIndex(false, false));
    }

    @Test
    void hoverUsesNativeHighlightedSprite() {
        assertEquals(
                2,
                ButtonSpriteState.spriteIndex(true, true));
    }

    @Test
    void idleUsesNativeNormalSprite() {
        assertEquals(
                0,
                ButtonSpriteState.spriteIndex(true, false));
    }

    @Test
    void focusRingIsDrawnOnlyForFocusedEnabledButton() {
        assertTrue(ButtonSpriteState.drawsFocusRing(true, true));
        assertFalse(ButtonSpriteState.drawsFocusRing(true, false));
        assertFalse(ButtonSpriteState.drawsFocusRing(false, true));
    }
}
