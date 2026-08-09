package com.kltyton.autoseamblend.texture.image;

import java.util.Arrays;

/** 中文：重载时生成与导出使用的不可变预乘 ARGB 图像。 / English: Immutable premultiplied-ARGB image used by reload-time generation and export. */
public final class ArgbImage {
    private final int width;
    private final int height;
    private final int[] pixels;

    private ArgbImage(int width, int height, int[] pixels, boolean trusted) {
        if (width <= 0 || height <= 0 || (long) width * height != pixels.length) {
            throw new IllegalArgumentException(
                    "Invalid image dimensions " + width + 'x' + height + " for " + pixels.length + " pixels");
        }
        for (int pixel : pixels) {
            PremultipliedArgb.requireValid(pixel);
        }
        this.width = width;
        this.height = height;
        this.pixels = trusted ? pixels : pixels.clone();
    }

    public static ArgbImage premultiplied(int width, int height, int[] pixels) {
        if (pixels == null) {
            throw new NullPointerException("pixels");
        }
        return new ArgbImage(width, height, pixels, false);
    }

    public static ArgbImage fromStraightArgb(int width, int height, int[] pixels) {
        if (pixels == null) {
            throw new NullPointerException("pixels");
        }
        int[] converted = new int[pixels.length];
        for (int index = 0; index < pixels.length; index++) {
            converted[index] = PremultipliedArgb.fromStraight(pixels[index]);
        }
        return new ArgbImage(width, height, converted, true);
    }

    public static ArgbImage wrapGenerated(int width, int height, int[] pixels) {
        return new ArgbImage(width, height, pixels, true);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int pixelAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Image coordinate outside image: " + x + ", " + y);
        }
        return pixels[y * width + x];
    }

    int pixelAtIndex(int index) {
        return pixels[index];
    }

    public int[] copyPixels() {
        return pixels.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ArgbImage image
                && width == image.width
                && height == image.height
                && Arrays.equals(pixels, image.pixels);
    }

    @Override
    public int hashCode() {
        int result = 31 * width + height;
        return 31 * result + Arrays.hashCode(pixels);
    }
}
