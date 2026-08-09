package com.kltyton.autoseamblend.frontend.uilib.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScrollBodyLayoutTest {
    @Test
    void rowsStackNonOverlappingWithFourPixelSpacing() {
        List<Integer> heights = List.of(42, 42, 42, 32);

        assertEquals(
                List.of(0, 46, 92, 138),
                ScrollBodyLayout.stackedYs(heights, 4));
    }

    @Test
    void contentHeightSumsRowsAndSpacing() {
        assertEquals(
                124,
                ScrollBodyLayout.contentHeight(
                        List.of(42, 42, 32),
                        4));
    }

    @Test
    void maxScrollIsContentBeyondViewport() {
        assertEquals(
                44,
                ScrollBodyLayout.maxScroll(124, 80));
    }

    @Test
    void offsetIsClampedToViewportBounds() {
        assertEquals(
                -44,
                ScrollBodyLayout.clampOffset(
                        -100,
                        124,
                        80));
        assertEquals(
                0,
                ScrollBodyLayout.clampOffset(
                        30,
                        124,
                        80));
    }

    @Test
    void offsetIsZeroWhenContentFits() {
        assertEquals(
                0,
                ScrollBodyLayout.clampOffset(
                        -10,
                        80,
                        80));
    }

    @Test
    void offsetIsRetainedAcrossRebuild() {
        int captured = -30;

        assertEquals(
                -30,
                ScrollBodyLayout.clampOffset(
                        captured,
                        124,
                        80));
    }
}
