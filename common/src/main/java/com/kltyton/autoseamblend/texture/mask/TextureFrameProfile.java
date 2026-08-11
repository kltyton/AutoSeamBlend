package com.kltyton.autoseamblend.texture.mask;

import java.util.Objects;

/**
 * 中文：归一化源纹理内缩量，用于移除已接受连接边且不把单像素拉伸为可见条带。
 *
 * English:
 * Normalized source-texture insets used to remove an accepted connection edge without stretching
 * a single pixel into a visible band.
 */
public record TextureFrameProfile(
        float left,
        float down,
        float right,
        float up) {
    private static final float MAX_INSET = 0.25F;

    public TextureFrameProfile {
        check("left", left);
        check("down", down);
        check("right", right);
        check("up", up);
    }

    public static TextureFrameProfile fromAlpha(
            int width,
            int height,
            boolean framedAlpha,
            PixelOpacity opacity) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "source dimensions must be positive");
        }
        Objects.requireNonNull(opacity, "opacity");
        if (!framedAlpha) {
            float horizontal =
                    Math.min(MAX_INSET, 1.0F / width);
            float vertical =
                    Math.min(MAX_INSET, 1.0F / height);
            return new TextureFrameProfile(
                    horizontal,
                    vertical,
                    horizontal,
                    vertical);
        }
        return new TextureFrameProfile(
                scan(width, height, opacity, Edge.LEFT)
                        / (float) width,
                scan(width, height, opacity, Edge.DOWN)
                        / (float) height,
                scan(width, height, opacity, Edge.RIGHT)
                        / (float) width,
                scan(width, height, opacity, Edge.UP)
                        / (float) height);
    }

    private static int scan(
            int width,
            int height,
            PixelOpacity opacity,
            Edge edge) {
        int limit = Math.max(
                1,
                Math.min(
                        edge.horizontal ? width : height,
                        Math.max(width, height) / 4));
        int length = edge.horizontal ? height : width;
        int accepted = 0;
        for (int depth = 0; depth < limit; depth++) {
            int opaque = 0;
            for (int along = 0; along < length; along++) {
                int x = switch (edge) {
                    case LEFT -> depth;
                    case RIGHT -> width - 1 - depth;
                    case UP, DOWN -> along;
                };
                int y = switch (edge) {
                    case UP -> depth;
                    case DOWN -> height - 1 - depth;
                    case LEFT, RIGHT -> along;
                };
                if (opacity.opaque(x, y)) {
                    opaque++;
                }
            }
            if (opaque * 2 < length) {
                break;
            }
            accepted++;
        }
        return Math.max(1, accepted);
    }

    private static void check(
            String name,
            float value) {
        if (!Float.isFinite(value)
                || value < 0.0F
                || value > MAX_INSET) {
            throw new IllegalArgumentException(
                    name + " must be finite and in [0,0.25]");
        }
    }

    @FunctionalInterface
    public interface PixelOpacity {
        boolean opaque(int x, int y);
    }

    private enum Edge {
        LEFT(true),
        DOWN(false),
        RIGHT(true),
        UP(false);

        private final boolean horizontal;

        Edge(boolean horizontal) {
            this.horizontal = horizontal;
        }
    }
}
