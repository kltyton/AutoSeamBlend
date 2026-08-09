package com.kltyton.autoseamblend.texture.analysis;

import java.util.Objects;

/**
 * 中文：提供与 Loader 无关的纯像素 alpha 特征分析；不读取资源、不触碰 Atlas。
 * English: Provides Loader-neutral pure-pixel alpha analysis without resource or Atlas access.
 */
public final class TexturePixelAnalysis {
    private TexturePixelAnalysis() {}

    public static boolean isOpaque(int[] pixels) {
        for (int pixel : Objects.requireNonNull(pixels, "pixels")) {
            if ((pixel >>> 24) != 0xFF) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasFramedAlpha(
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int[] pixels) {
        Objects.requireNonNull(pixels, "pixels");
        if (frameWidth < 3 || frameHeight < 3) {
            return false;
        }
        int borderWidth = Math.max(1, Math.min(frameWidth, frameHeight) * 3 / 16);
        if (borderWidth * 2 >= frameWidth || borderWidth * 2 >= frameHeight) {
            return false;
        }
        long borderPixels = 0;
        long opaqueBorderPixels = 0;
        long interiorPixels = 0;
        long transparentInteriorPixels = 0;
        for (int y = 0; y < sheetHeight; y++) {
            int localY = y % frameHeight;
            for (int x = 0; x < sheetWidth; x++) {
                int localX = x % frameWidth;
                int alpha = pixels[y * sheetWidth + x] >>> 24;
                boolean edge = localX < borderWidth
                        || localX >= frameWidth - borderWidth
                        || localY < borderWidth
                        || localY >= frameHeight - borderWidth;
                if (edge) {
                    borderPixels++;
                    if (alpha >= 128) {
                        opaqueBorderPixels++;
                    }
                } else {
                    interiorPixels++;
                    if (alpha <= 16) {
                        transparentInteriorPixels++;
                    }
                }
            }
        }
        return borderPixels > 0
                && interiorPixels > 0
                && opaqueBorderPixels * 4 >= borderPixels
                && transparentInteriorPixels * 5 >= interiorPixels * 3;
    }
}
