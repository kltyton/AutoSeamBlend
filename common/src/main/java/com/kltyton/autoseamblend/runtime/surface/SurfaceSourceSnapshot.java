package com.kltyton.autoseamblend.runtime.surface;

import java.util.Objects;

/**
 * 中文：资源重载期间冻结的项目自有源精灵值，不含 Identifier、Sprite 或 Loader 类型。
 *
 * English: Project-owned source-sprite values frozen during a resource reload; it contains no
 * Identifier, Sprite, or Loader type.
 */
public record SurfaceSourceSnapshot(
        String spriteId,
        int sheetWidth,
        int sheetHeight,
        int frameWidth,
        int frameHeight,
        int[] straightArgb,
        boolean animated,
        boolean opaque,
        boolean framedAlpha,
        SurfaceSourceProvenance provenance) {
    public SurfaceSourceSnapshot {
        if (spriteId == null || spriteId.isBlank()) {
            throw new IllegalArgumentException("source sprite id must not be blank");
        }
        Objects.requireNonNull(provenance, "provenance");
        if (sheetWidth <= 0
                || sheetHeight <= 0
                || frameWidth <= 0
                || frameHeight <= 0
                || sheetWidth % frameWidth != 0
                || sheetHeight % frameHeight != 0) {
            throw new IllegalArgumentException("invalid source image dimensions");
        }
        int[] pixels = Objects.requireNonNull(straightArgb, "straightArgb");
        straightArgb = pixels.clone();
        if (pixels.length != Math.multiplyExact(sheetWidth, sheetHeight)) {
            throw new IllegalArgumentException("source pixel count differs from sheet size");
        }
    }

    @Override
    public int[] straightArgb() {
        return straightArgb.clone();
    }
}
