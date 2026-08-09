package com.kltyton.autoseamblend.texture.mask;

import com.kltyton.autoseamblend.texture.image.PremultipliedArgb;
import java.util.Objects;

/**
 * 中文：MCPatcher 或 Continuity overlay-17 状态的不可变硬裁切几何。紧凑二值拓扑库源自 Kai1907 的 MIT 许可 Block Overlays 资源包，不存储纹理像素。
 *
 * English:
 * Immutable hard-cutout geometry for MCPatcher/Continuity overlay-17 states.
 *
 * <p>The compact binary topology library is derived from the MIT-licensed Block Overlays pack by
 * Kai1907. It stores no texture pixels: each selected topology cuts the player's current source
 * texture, so normal rendering never creates or reads generated PNG files.
 */
public final class OverlayCutoutProfile {
    private static final int SIZE = 16;
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private static final String[][] COMPACT_ROWS = {
        {
            "0000000000000000000000000000000000000000000000000000C0006000C000",
            "0000000000000000000000000000000000000000000000006004B82EDEFB779F",
            "0000000000000000000000000000000000000000000000000000000000030005",
            "C000E000B000E000C000E000C0008000C000E0004000C000E004D9AEF77DFFEF",
            "0003000500070002000100030007000300070005000F000F0025627FB7F7FEFF",
            "E0034007F00FE005A003C0068003C001E003C007C0036005E807DE4BFBBFFEFF",
            "FFFFEDDF5877000F000700030001000300070005000300076007F06DDDFFFFDF",
            "C000A000F00040006000C0008000C000C000A0006000C0008000C0006000B000",
            "EF72CBDD7537A00AE001C0018003C00760034001C003A007E102766DDBB77EED",
            "000500020002000100030006000D0006000300070006000B0007000500070002",
            "F7FFFEBBD8D1E000C000C000E0006000C000C000A000C0008000C0006000C000",
            "FF7EF7FF993D000F00050003000700070003000300070005000300030005000F",
            "FFFBFDBFF30DA004E000C0008000C0004000E000C000E000A004F31EF7BDFFEF",
            "FFDFEDFFF82FE005B00FE003C00340014003C007C007E00DA007C003E006D00F",
            "4000E00040008000000000000000000000000000000000000000000000000000",
            "EE7FFBDB990D0004000000000000000000000000000000000000000000000000",
            "0002000700010000000000000000000000000000000000000000000000000000"
        },
        {
            "0000000000000000000000000000000000000000000000006000F000D000E000",
            "0000000000000000000000000000000000000000000000000402DE37FEFFFFFF",
            "0000000000000000000000000000000000000000000000000008000D0007000F",
            "E000F000E000C000E000E000F000E000C000E000F000D000F800FB03FFE7FFFF",
            "0007000F000700030007000F00070003000B0007000700038C27DE7FFFFFFFFF",
            "E00FF007F003E007C00FE00FF007E003C00BE00FE007F003E897FDBFFFFFFFFF",
            "FFFFFFFFEECF8447000F000F0007000300030007000F000B0857DDFFFFFFFFFF",
            "E000F000E000C000E000F000F000E000C000E000F000E000E000C000E000F000",
            "FFFFFBF7E8C3D987F003E007C00FC00FE007F003C007E20FC703EF37FFFFFFFF",
            "00070007000F0007000300070007000F000F0007000300030007000F000F0007",
            "FFFFFFFFEF77E621C000E000E000F000F000C000E000E000F000E000C000C000",
            "FFFFFFFF6F370607000F000700030007000F0007000300030007000F000F0007",
            "FFFFFFFFDBDFE18CE000C000E000F000F000E000E000C000810CE79FFFFFFFFF",
            "FFFFFFFFCCF5D861E003C003E007F00FF007E003C007C00FE00FF007E007C00F",
            "F000E000E0004000000000000000000000000000000000000000000000000000",
            "FFFFF7FFF3736030000000000000000000000000000000000000000000000000",
            "000F000700030001000000000000000000000000000000000000000000000000"
        },
        {
            "0000000000000000000000000000000000000000000000000000E000F000F000",
            "00000000000000000000000000000000000000000000000C003EB83FFCFFFFFF",
            "0000000000000000000000000000000000000000000000000000000100030003",
            "F000F000E000C00080008000C000C000E000F000F000E00CE03EF83FFCFFFFFF",
            "00010001000100030003000300070007000300010003000F003FB83FFCFFFFFF",
            "F001F001E001C00380038003C007C007E003F001F003E00FE03FF83FFCFFFFFF",
            "FFFFFFFFFFFFC7EF01C7000300070007000300010003000F003FB83FFCFFFFFF",
            "F000F000E000C00080008000C000C000E000F000F000E000C000E000F000F000",
            "FFFFFFFFFFFFC7EF83C7C003C007C007E003F001F003F81FF83FF83FFCFFFFFF",
            "0001000100010003000300030007000700030001000300030003000100000000",
            "FFFFFFFFFFFFC7EE81C08000C000C000E000F000F000E000C000E000F000F000",
            "FFFFFFFFFFFFC7EF81C700030007000700030001000300030003000100000000",
            "FFFFFFFFFFFFC7EE81C08000C000C000E000F000F000E00CE03EF83FFCFFFFFF",
            "FFFFFFFFFFFFC7EF81C78003C007C007E003F001F003E003C0038001E000F000",
            "F800F800F000C000000000000000000000000000000000000000000000000000",
            "FFFFFFFFFFFFC7EE01C000000000000000000000000000000000000000000000",
            "0001000100010000000000000000000000000000000000000000000000000000"
        },
        {
            "000000000000000000000000000000000000000000000000800080006000E000",
            "0000000000000000000000000000000000000000000000001024BA6ECFB9FBFF",
            "000000000000000000000000000000000000000000000000000000030005000A",
            "C000A000E0007000E000A000E000C000C0007000E000E0007104FFAEDDF7F7BF",
            "000600030005000F0005000700030007000300070007800DE107F7ADBDDFEFF7",
            "A003E007E00D7007E003C00F8007C005E0036007D007F00DB10FFFBBADFEFFEF",
            "F77FBFF7F9FF104D0007000E0003000700030005000B0007610DF79B9DBFEFF7",
            "8000E000B00040006000C0008000C0006000B000E000C0008000C000A000E000",
            "FFBFB6F7F3ED7047C007E00FB003E007C0036001F003E007711DF7ABADDFFFFF",
            "000300070005000F0007000200030007000D00060003000100030007000D0006",
            "FFFDFDEFD789F100E8006000D000E000E0006000F000C000C000E0008000C000",
            "FFFFBFED66FF00770017000F000D000700030006000300030007000500070003",
            "FFBFBBE7D6EDC044C000E000B000C000E000F000C000E000B108759EEEEDFDEF",
            "FFEEDBBFB4FDF043C007A00DF0076006C003E007B003E00DC007E003A003F007",
            "4000C00080000000000000000000000000000000000000000000000000000000",
            "EBFBB6EF9C740820000000000000000000000000000000000000000000000000",
            "0007000500030000000000000000000000000000000000000000000000000000"
        },
        {
            "0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "000000008000C0006000C0008000C0006000A000E000C0008000C000A000E000",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "00000000000100030007000200030007000D00060003000100030007000D0006",
            "FFFDFDEFD789F100E8006000D000E000E0006000F000C000C000E0008000C000",
            "FFFFBFED66FF00770017000F000D000700030006000300030007000500070003",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "FFEEDBBFB4FDF043C007A00DF0076006C003E007B003E00DC007E003A003F007",
            "4000C00080000000000000000000000000000000000000000000000000000000",
            "EBFBB6EF9C740820000000000000000000000000000000000000000000000000",
            "0007000500030000000000000000000000000000000000000000000000000000"
        }
    };

    private static final int[][][] MASKS = decode();

    private final int[][] slotRows;
    private final int topologyId;
    private final boolean fillsTopFringeFromSubstrate;
    private final int dominance;
    private final long visualSignature;

    private OverlayCutoutProfile(
            int[][] slotRows,
            int topologyId,
            boolean fillsTopFringeFromSubstrate,
            int dominance,
            long visualSignature) {
        this.slotRows = slotRows;
        this.topologyId = topologyId;
        this.fillsTopFringeFromSubstrate =
                fillsTopFringeFromSubstrate;
        this.dominance = dominance;
        this.visualSignature = visualSignature;
    }

    public static OverlayCutoutProfile fromArgb(
            int width,
            int height,
            int[] pixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("source dimensions must be positive");
        }
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("pixel count does not match source dimensions");
        }
        int template = template(width, height, pixels);
        return new OverlayCutoutProfile(
                MASKS[template],
                template,
                template == 4,
                dominance(template),
                visualSignature(width, height, pixels));
    }

    public static OverlayCutoutProfile thinUniform() {
        return new OverlayCutoutProfile(
                MASKS[2],
                2,
                false,
                dominance(2),
                0L);
    }

    /**
     * 中文：原始 PNG 不含运行时 tint 颜色时，使用专门的稀疏有色表面轮廓；已识别的分层侧面仍保留其基底采样语义。
     *
     * English: Uses the sparse tinted-surface topology when the raw PNG omits its runtime tint;
     * an already-detected layered side keeps its substrate-sampling semantics.
     */
    public OverlayCutoutProfile forTintedSurface() {
        if (topologyId == 1 || topologyId == 4) {
            return this;
        }
        return new OverlayCutoutProfile(
                MASKS[1],
                1,
                false,
                dominance(1),
                visualSignature);
    }

    /** 中文：返回确定 Atlas/导出变体的纯拓扑编号。 / English: Returns the topology id used to key Atlas and export variants. */
    public int topologyId() {
        return topologyId;
    }

    /** 中文：返回由纯纹理统计得到的 overlay 供体层级；数值越大越适合覆盖相邻接收面。 / English: Returns the overlay-donor tier derived only from texture statistics; larger values dominate adjacent receivers. */
    public int dominance() {
        return dominance;
    }

    /** 中文：返回像素内容签名，只用于同一视觉层级的确定性平局，不识别任何方块或文件名。 / English: Returns a pixel-content signature used only for deterministic ties inside one visual tier; it identifies no block or file name. */
    public long visualSignature() {
        return visualSignature;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof OverlayCutoutProfile that
                && topologyId == that.topologyId
                && fillsTopFringeFromSubstrate
                        == that.fillsTopFringeFromSubstrate
                && dominance == that.dominance
                && visualSignature == that.visualSignature;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                topologyId,
                fillsTopFringeFromSubstrate,
                dominance,
                visualSignature);
    }

    public int rowBits(int tileMask, int y) {
        if (y < 0 || y >= SIZE) {
            throw new IllegalArgumentException("overlay row must be in [0,15]");
        }
        int row = 0;
        int remaining = tileMask & 0x1FFFF;
        while (remaining != 0) {
            int tile = Integer.numberOfTrailingZeros(remaining);
            row |= slotRows[tile][y];
            remaining &= remaining - 1;
        }
        return row;
    }

    /**
     * 中文：为一个生成的 overlay 像素采样供体材质；通过从纹理基底半区采样匹配的侧面轮廓，覆盖内部接缝且不嵌入生物群系颜色。
     *
     * English:
     * Samples the donor material for one generated overlay pixel.
     *
     * <p>An opaque side texture can contain a narrow chromatic top fringe that is already rendered
     * by the donor block's own tinted face. Reusing those fringe texels for the extra overlay keeps
     * the internal seam visible. The matching side profile instead samples the same column from the
     * substrate half of the texture, so the generated overlay covers that seam without embedding a
     * biome color.
     */
    public int sampleArgb(
            int width,
            int height,
            int[] pixels,
            int x,
            int y) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "source dimensions must be positive");
        }
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException(
                    "pixel count does not match source dimensions");
        }
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IllegalArgumentException(
                    "source coordinate is outside the image");
        }
        int pixel = pixels[y * width + x];
        if (!fillsTopFringeFromSubstrate
                || !chromaticTopFringe(pixel)) {
            return pixel;
        }
        int substrateY = Math.min(
                height - 1,
                y + height / 2);
        return pixels[substrateY * width + x];
    }

    private static boolean chromaticTopFringe(int pixel) {
        int red = PremultipliedArgb.red(pixel);
        int green = PremultipliedArgb.green(pixel);
        int blue = PremultipliedArgb.blue(pixel);
        return PremultipliedArgb.alpha(pixel) != 0
                && green * 10 > red * 11
                && green * 4 > blue * 5;
    }

    private static int template(int width, int height, int[] pixels) {
        if (layeredTopFringe(width, height, pixels)) {
            return 4;
        }

        long red = 0;
        long green = 0;
        long blue = 0;
        long count = 0;
        for (int pixel : pixels) {
            int alpha = PremultipliedArgb.alpha(pixel);
            if (alpha == 0) {
                continue;
            }
            red += PremultipliedArgb.red(pixel) * 255L / alpha;
            green += PremultipliedArgb.green(pixel) * 255L / alpha;
            blue += PremultipliedArgb.blue(pixel) * 255L / alpha;
            count++;
        }
        if (count == 0) {
            return 2;
        }
        red /= count;
        green /= count;
        blue /= count;
        if (green > red * 11 / 10 && green > blue * 5 / 4) {
            return 1;
        }
        long brightness = (red + green + blue) / 3;
        if (brightness >= 145 && red > blue * 6 / 5) {
            return 0;
        }
        if (Math.max(red, Math.max(green, blue))
                - Math.min(red, Math.min(green, blue)) < 22) {
            return 2;
        }
        return 3;
    }

    /**
     * 中文：识别“顶部有色薄层、下部为基底”的通用纹理结构；该结构适用于任意资源包或第三方方块，不依赖原版 PNG 指纹。
     *
     * English:
     * Detects a generic colored top fringe over a substrate. The observation works for arbitrary
     * resource packs and third-party blocks and never depends on vanilla PNG fingerprints.
     */
    private static boolean layeredTopFringe(
            int width,
            int height,
            int[] pixels) {
        int split = Math.max(1, height / 2);
        int topVisible = 0;
        int topFringe = 0;
        int bottomVisible = 0;
        int bottomFringe = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                if (PremultipliedArgb.alpha(pixel) == 0) {
                    continue;
                }
                if (y < split) {
                    topVisible++;
                    if (chromaticTopFringe(pixel)) {
                        topFringe++;
                    }
                } else {
                    bottomVisible++;
                    if (chromaticTopFringe(pixel)) {
                        bottomFringe++;
                    }
                }
            }
        }
        return topFringe >= Math.max(1, width / 2)
                && topFringe * 8 >= Math.max(1, topVisible)
                && (bottomVisible == 0
                        || bottomFringe * 16 < bottomVisible);
    }

    private static int dominance(int template) {
        return switch (template) {
            case 4 -> 5;
            case 1 -> 4;
            case 3 -> 3;
            case 0 -> 2;
            case 2 -> 1;
            default -> throw new IllegalArgumentException(
                    "unknown overlay texture class: " + template);
        };
    }

    private static long visualSignature(
            int width,
            int height,
            int[] pixels) {
        long hash = FNV_OFFSET;
        hash = (hash ^ width) * FNV_PRIME;
        hash = (hash ^ height) * FNV_PRIME;
        for (int pixel : pixels) {
            hash = (hash ^ Integer.toUnsignedLong(pixel)) * FNV_PRIME;
        }
        return hash;
    }

    private static int[][][] decode() {
        int[][][] decoded = new int[COMPACT_ROWS.length][17][SIZE];
        for (int template = 0; template < COMPACT_ROWS.length; template++) {
            for (int slot = 0; slot < 17; slot++) {
                String compact = COMPACT_ROWS[template][slot];
                if (compact.length() != SIZE * 4) {
                    throw new IllegalStateException("invalid compact overlay mask");
                }
                for (int row = 0; row < SIZE; row++) {
                    decoded[template][slot][row] =
                            Integer.parseUnsignedInt(
                                    compact.substring(row * 4, row * 4 + 4),
                                    16);
                }
            }
        }
        return decoded;
    }
}
