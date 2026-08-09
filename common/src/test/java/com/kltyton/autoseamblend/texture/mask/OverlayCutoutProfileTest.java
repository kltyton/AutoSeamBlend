package com.kltyton.autoseamblend.texture.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OverlayCutoutProfileTest {
    private static final int SIZE = 16;

    @Test
    void arbitraryTexturesUseObservableVisualClasses() {
        OverlayCutoutProfile mineral = uniform(0xFF808080);
        OverlayCutoutProfile granular = uniform(0xFFF0C080);
        OverlayCutoutProfile substrate = uniform(0xFF8B5A2B);
        OverlayCutoutProfile foliage = uniform(0xFF50A040);
        OverlayCutoutProfile layered = layered(
                0xFF50A040,
                0xFF8B5A2B);

        assertTrue(mineral.dominance() < granular.dominance());
        assertTrue(granular.dominance() < substrate.dominance());
        assertTrue(substrate.dominance() < foliage.dominance());
        assertTrue(foliage.dominance() < layered.dominance());
    }

    @Test
    void sameVisualClassUsesPixelsOnlyForDeterministicTies() {
        OverlayCutoutProfile first = uniform(0xFF747474);
        OverlayCutoutProfile second = uniform(0xFF888888);

        assertEquals(first.dominance(), second.dominance());
        assertNotEquals(
                first.visualSignature(),
                second.visualSignature());
    }

    private static OverlayCutoutProfile uniform(int color) {
        int[] pixels = new int[SIZE * SIZE];
        Arrays.fill(pixels, color);
        return OverlayCutoutProfile.fromArgb(
                SIZE,
                SIZE,
                pixels);
    }

    private static OverlayCutoutProfile layered(
            int fringe,
            int substrate) {
        int[] pixels = new int[SIZE * SIZE];
        Arrays.fill(pixels, substrate);
        Arrays.fill(pixels, 0, SIZE, fringe);
        return OverlayCutoutProfile.fromArgb(
                SIZE,
                SIZE,
                pixels);
    }
}
