package com.kltyton.autoseamblend.texture.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TextureBasisTest {
    @Test
    void canonicalBasesMatchVanillaAxisAlignedFaceOrientation() {
        assertBasis(WorldDirection.UP, WorldDirection.EAST, WorldDirection.SOUTH);
        assertBasis(WorldDirection.DOWN, WorldDirection.EAST, WorldDirection.NORTH);
        assertBasis(WorldDirection.NORTH, WorldDirection.WEST, WorldDirection.DOWN);
        assertBasis(WorldDirection.SOUTH, WorldDirection.EAST, WorldDirection.DOWN);
        assertBasis(WorldDirection.WEST, WorldDirection.SOUTH, WorldDirection.DOWN);
        assertBasis(WorldDirection.EAST, WorldDirection.NORTH, WorldDirection.DOWN);
    }

    @Test
    void mapsEdgesAndCornersUsingUvGradients() {
        TextureBasis basis = TextureBasis.fromUvGradients(
                WorldDirection.SOUTH,
                new UvGradient(1.0, 0.0, 0.0),
                new UvGradient(0.0, -2.0, 0.0));

        assertEquals(WorldDirection.EAST.offset(), basis.offset(TextureEdge.RIGHT));
        assertEquals(WorldDirection.DOWN.offset(), basis.offset(TextureEdge.DOWN));
        assertEquals(new WorldOffset(-1, -1, 0), basis.offset(TextureCorner.BOTTOM_LEFT));
    }

    @Test
    void rejectsDegenerateSkewedAndOutOfPlaneGradients() {
        assertThrows(IllegalArgumentException.class, () -> TextureBasis.fromUvGradients(
                WorldDirection.UP, new UvGradient(0, 0, 0), new UvGradient(0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> TextureBasis.fromUvGradients(
                WorldDirection.UP, new UvGradient(1, 0, 1), new UvGradient(0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> TextureBasis.fromUvGradients(
                WorldDirection.UP, new UvGradient(0, 1, 0), new UvGradient(0, 0, 1)));
    }

    private static void assertBasis(
            WorldDirection face, WorldDirection expectedRight, WorldDirection expectedDown) {
        TextureBasis basis = TextureBasis.canonical(face);
        assertEquals(expectedRight, basis.right());
        assertEquals(expectedDown, basis.down());
        assertEquals(expectedRight.opposite(), basis.left());
        assertEquals(expectedDown.opposite(), basis.up());
    }
}
