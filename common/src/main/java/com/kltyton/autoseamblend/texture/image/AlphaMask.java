package com.kltyton.autoseamblend.texture.image;

import java.util.Arrays;

/** 中文：不可变的 8 位 alpha 蒙版。 / English: Immutable 8-bit alpha mask. */
public final class AlphaMask {
    private final int width;
    private final int height;
    private final byte[] alpha;

    private AlphaMask(int width, int height, byte[] alpha, boolean trusted) {
        checkDimensions(width, height, alpha.length);
        this.width = width;
        this.height = height;
        this.alpha = trusted ? alpha : alpha.clone();
    }

    public static AlphaMask of(int width, int height, byte[] alpha) {
        if (alpha == null) {
            throw new NullPointerException("alpha");
        }
        return new AlphaMask(width, height, alpha, false);
    }

    public static AlphaMask wrapGenerated(int width, int height, byte[] alpha) {
        return new AlphaMask(width, height, alpha, true);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int alphaAt(int x, int y) {
        checkCoordinates(x, y);
        return Byte.toUnsignedInt(alpha[y * width + x]);
    }

    public int alphaAtIndex(int index) {
        return Byte.toUnsignedInt(alpha[index]);
    }

    public byte[] copyAlpha() {
        return alpha.clone();
    }

    public boolean isDisjoint(AlphaMask other) {
        if (other.width != width || other.height != height) {
            throw new IllegalArgumentException("Mask dimensions differ");
        }
        for (int index = 0; index < alpha.length; index++) {
            if (alpha[index] != 0 && other.alpha[index] != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AlphaMask mask
                && width == mask.width
                && height == mask.height
                && Arrays.equals(alpha, mask.alpha);
    }

    @Override
    public int hashCode() {
        int result = 31 * width + height;
        return 31 * result + Arrays.hashCode(alpha);
    }

    private static void checkDimensions(int width, int height, int length) {
        if (width <= 0 || height <= 0 || (long) width * height != length) {
            throw new IllegalArgumentException(
                    "Invalid mask dimensions " + width + 'x' + height + " for " + length + " samples");
        }
    }

    private void checkCoordinates(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Mask coordinate outside image: " + x + ", " + y);
        }
    }
}
