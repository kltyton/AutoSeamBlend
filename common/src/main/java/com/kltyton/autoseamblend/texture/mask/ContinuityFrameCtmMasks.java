package com.kltyton.autoseamblend.texture.mask;

import com.kltyton.autoseamblend.texture.image.ArgbImage;
import java.util.Optional;

/**
 * 中文：NeoContinuity 标准框架玻璃 CTM 状态的紧凑二值保留蒙版。拓扑源自 NeoContinuity 的 LGPL-3.0-only 内置默认资源包，不包含复制的颜色像素；每个状态从当前源纹理保留像素。
 *
 * English:
 * Compact binary retain masks for NeoContinuity's standard framed-glass CTM states.
 *
 * <p>The topology is derived from NeoContinuity's LGPL-3.0-only built-in default resource pack.
 * It contains no copied color pixels. Each state retains pixels from the active source texture,
 * allowing glass and panes from the current resource stack to use the native 47-state layout
 * without generated PNG resources.
 */
public final class ContinuityFrameCtmMasks {
    private static final int SIZE = 16;
    private static final String[] COMPACT_ROWS = {
        "FFFF80018011800980058001800180018001800180018001A00190018001FFFF",
        "FFFF00010011000900050001000100010001000100010001200110010001FFFF",
        "FFFF00000010000800040000000000000000000000000000200010000000FFFF",
        "FFFF80008010800880048000800080008000800080008000A00090008000FFFF",
        "FFFF000100110009000500010001000100010001000100012001100100018001",
        "FFFF80008010800880048000800080008000800080008000A000900080008001",
        "8001000100110009000500010001000100010001000100012001100100018001",
        "FFFF000000100008000400000000000000000000000000002000100000008001",
        "0001000000100008000400000000000000000000000000002000100000008001",
        "8001000000100008000400000000000000000000000000002000100000000001",
        "8000000000100008000400000000000000000000000000002000100000008000",
        "0000000000100008000400000000000000000000000000002000100000008001",
        "FFFF80018011800980058001800180018001800180018001A001900180018001",
        "FFFF000100110009000500010001000100010001000100012001100100010001",
        "FFFF000000100008000400000000000000000000000000002000100000000000",
        "FFFF80008010800880048000800080008000800080008000A000900080008000",
        "800100010011000900050001000100010001000100010001200110010001FFFF",
        "800180008010800880048000800080008000800080008000A00090008000FFFF",
        "800100000010000800040000000000000000000000000000200010000000FFFF",
        "800180008010800880048000800080008000800080008000A000900080008001",
        "8000000000100008000400000000000000000000000000002000100000008001",
        "8001000000100008000400000000000000000000000000002000100000008000",
        "8001000000100008000400000000000000000000000000002000100000000000",
        "0001000000100008000400000000000000000000000000002000100000000001",
        "800180018011800980058001800180018001800180018001A001900180018001",
        "0001000100110009000500010001000100010001000100012001100100010001",
        "0000000000100008000400000000000000000000000000002000100000000000",
        "800080008010800880048000800080008000800080008000A000900080008000",
        "8001000100110009000500010001000100010001000100012001100100010001",
        "FFFF000000100008000400000000000000000000000000002000100000008000",
        "0001000100110009000500010001000100010001000100012001100100018001",
        "FFFF000000100008000400000000000000000000000000002000100000000001",
        "0000000000100008000400000000000000000000000000002000100000008000",
        "0000000000100008000400000000000000000000000000002000100000000001",
        "0001000000100008000400000000000000000000000000002000100000008000",
        "8000000000100008000400000000000000000000000000002000100000000001",
        "800180018011800980058001800180018001800180018001A00190018001FFFF",
        "000100010011000900050001000100010001000100010001200110010001FFFF",
        "000000000010000800040000000000000000000000000000200010000000FFFF",
        "800080008010800880048000800080008000800080008000A00090008000FFFF",
        "000100000010000800040000000000000000000000000000200010000000FFFF",
        "800080008010800880048000800080008000800080008000A000900080008001",
        "800000000010000800040000000000000000000000000000200010000000FFFF",
        "800180008010800880048000800080008000800080008000A000900080008000",
        "8000000000100008000400000000000000000000000000002000100000000000",
        "0001000000100008000400000000000000000000000000002000100000000000",
        "8001000000100008000400000000000000000000000000002000100000008001"
    };
    private static final int[][] ROWS = decode();

    private ContinuityFrameCtmMasks() {}

    public static Optional<ArgbImage> apply(ArgbImage source, int tileIndex) {
        if (tileIndex < 0 || tileIndex >= ROWS.length) {
            throw new IllegalArgumentException("CTM tile must be in [0,46]");
        }
        if (!framedAlpha(source)) {
            return Optional.empty();
        }
        int[] pixels = source.copyPixels();
        int[] mask = ROWS[tileIndex];
        for (int y = 0; y < source.height(); y++) {
            int maskY = y * SIZE / source.height();
            int row = mask[maskY];
            for (int x = 0; x < source.width(); x++) {
                int maskX = x * SIZE / source.width();
                if ((row & 1 << maskX) == 0) {
                    pixels[y * source.width() + x] = 0;
                }
            }
        }
        return Optional.of(ArgbImage.wrapGenerated(
                source.width(),
                source.height(),
                pixels));
    }

    private static boolean framedAlpha(ArgbImage source) {
        int width = source.width();
        int height = source.height();
        if (width < 3 || height < 3) {
            return false;
        }
        int borderWidth = Math.max(1, Math.min(width, height) * 3 / SIZE);
        if (borderWidth * 2 >= width || borderWidth * 2 >= height) {
            return false;
        }
        long border = 0;
        long opaqueBorder = 0;
        long interior = 0;
        long transparentInterior = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x < borderWidth
                        || x >= width - borderWidth
                        || y < borderWidth
                        || y >= height - borderWidth;
                int alpha = source.pixelAt(x, y) >>> 24;
                if (edge) {
                    border++;
                    if (alpha >= 128) {
                        opaqueBorder++;
                    }
                } else {
                    interior++;
                    if (alpha <= 16) {
                        transparentInterior++;
                    }
                }
            }
        }
        return border > 0
                && interior > 0
                && opaqueBorder * 4 >= border
                && transparentInterior * 5 >= interior * 3;
    }

    private static int[][] decode() {
        int[][] decoded = new int[COMPACT_ROWS.length][SIZE];
        for (int tile = 0; tile < COMPACT_ROWS.length; tile++) {
            String compact = COMPACT_ROWS[tile];
            if (compact.length() != SIZE * 4) {
                throw new IllegalStateException("invalid compact CTM frame mask");
            }
            for (int row = 0; row < SIZE; row++) {
                decoded[tile][row] = Integer.parseUnsignedInt(
                        compact.substring(row * 4, row * 4 + 4),
                        16);
            }
        }
        return decoded;
    }
}
