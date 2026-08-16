package com.kltyton.autoseamblend.texture.atlas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.texture.analysis.TexturePixelAnalysis;
import java.util.Random;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

/**
 * 中文：验证 Direct/Derived capture 单趟像素分析的逐位等价性与边界行为。
 *
 * <p>English: Verifies bitwise equivalence and boundary behavior of the single-pass pixel
 * analysis used by Direct/Derived capture. The analysis must produce exactly the same straight
 * ARGB pixels, opacity flag, and framed-alpha flag as {@code ARGB.fromABGR} applied per pixel
 * followed by {@link TexturePixelAnalysis}.
 */
final class InitialBlockAtlasResourcesPixelAnalysisTest {
    private static final long SEED = 0x5EED_2026L;

    @Test
    void randomSheetsMatchReferenceAnalysisBitwise() {
        Random random = new Random(SEED);
        int[][] sizes = {
            {1, 1, 1, 1},
            {2, 2, 2, 2},
            {4, 4, 4, 4},
            {8, 8, 8, 8},
            {16, 16, 16, 16},
            {16, 16, 4, 4},
            {32, 32, 16, 16},
            {32, 16, 16, 16},
            {48, 32, 16, 16},
            {48, 32, 8, 8},
        };
        for (int[] size : sizes) {
            int sheetWidth = size[0];
            int sheetHeight = size[1];
            int frameWidth = size[2];
            int frameHeight = size[3];
            for (int trial = 0; trial < 6; trial++) {
                assertMatchesReference(
                        randomPixels(
                                random,
                                sheetWidth * sheetHeight),
                        sheetWidth,
                        sheetHeight,
                        frameWidth,
                        frameHeight);
            }
        }
    }

    @Test
    void opaqueSheetIsOpaqueAndNeverFramed() {
        int[] pixels = uniform(0xFF808080, 16 * 16);
        assertMatchesReference(pixels, 16, 16, 16, 16);
        InitialBlockAtlasResources.PixelAnalysis analysis =
                analyze(pixels, 16, 16, 16, 16);
        assertTrue(analysis.opaque());
        assertFalse(analysis.framedAlpha());
    }

    @Test
    void fullyTransparentSheetIsNotOpaqueAndNeverFramed() {
        int[] pixels = uniform(0x00000000, 16 * 16);
        assertMatchesReference(pixels, 16, 16, 16, 16);
        InitialBlockAtlasResources.PixelAnalysis analysis =
                analyze(pixels, 16, 16, 16, 16);
        assertFalse(analysis.opaque());
        assertFalse(analysis.framedAlpha());
    }

    @Test
    void framedGlassSheetIsFramed() {
        int[] pixels = framedRing(16, 16, 16, 16, 0xFFFFFFFF, 0x00000000);
        assertMatchesReference(pixels, 16, 16, 16, 16);
        InitialBlockAtlasResources.PixelAnalysis analysis =
                analyze(pixels, 16, 16, 16, 16);
        assertFalse(analysis.opaque());
        assertTrue(analysis.framedAlpha());
    }

    @Test
    void translucentUniformSheetIsNotFramed() {
        int[] pixels = uniform(0x80808080, 16 * 16);
        assertMatchesReference(pixels, 16, 16, 16, 16);
        InitialBlockAtlasResources.PixelAnalysis analysis =
                analyze(pixels, 16, 16, 16, 16);
        assertFalse(analysis.opaque());
        assertFalse(analysis.framedAlpha());
    }

    @Test
    void tinyFramesAreNeverFramed() {
        int[] square = framedRing(2, 2, 2, 2, 0xFFFFFFFF, 0x00000000);
        assertMatchesReference(square, 2, 2, 2, 2);
        InitialBlockAtlasResources.PixelAnalysis squareAnalysis =
                analyze(square, 2, 2, 2, 2);
        assertFalse(squareAnalysis.framedAlpha());

        int[] tall = framedRing(4, 2, 4, 2, 0xFFFFFFFF, 0x00000000);
        assertMatchesReference(tall, 4, 2, 4, 2);
        InitialBlockAtlasResources.PixelAnalysis tallAnalysis =
                analyze(tall, 4, 2, 4, 2);
        assertFalse(tallAnalysis.framedAlpha());
    }

    @Test
    void threeByThreeCenterHoleIsFramed() {
        // 3x3 帧：legacy 阈值 borderWidth=max(1, min*3/16)=1，且 1*2>=3 不成立，
        // 因此单中心透明孔满足边框全不透明 + 内部全透明，framedAlpha 必须为 true。
        int[] pixels = framedRing(3, 3, 3, 3, 0xFFFFFFFF, 0x00000000);
        assertMatchesReference(pixels, 3, 3, 3, 3);
        InitialBlockAtlasResources.PixelAnalysis analysis =
                analyze(pixels, 3, 3, 3, 3);
        assertFalse(analysis.opaque());
        assertTrue(analysis.framedAlpha());
    }

    @Test
    void animatedSheetStatsCoverAllFrames() {
        // 两个 16x16 动画帧都保留完整边框：与单帧统计翻倍一致，结果必须仍为 framed。
        int[] pixels = new int[32 * 16];
        fillFramedRing(
                pixels,
                32,
                16,
                16,
                16,
                0,
                0,
                0xFFFFFFFF,
                0x00000000);
        fillFramedRing(
                pixels,
                32,
                16,
                16,
                16,
                16,
                0,
                0xFFFFFFFF,
                0x00000000);
        assertMatchesReference(pixels, 32, 16, 16, 16);
        InitialBlockAtlasResources.PixelAnalysis analysis =
                analyze(pixels, 32, 16, 16, 16);
        assertFalse(analysis.opaque());
        assertTrue(analysis.framedAlpha());
    }

    @Test
    void nonDivisibleSheetStillMatchesReference() {
        Random random = new Random(SEED ^ 0xABCD_EF01L);
        for (int trial = 0; trial < 4; trial++) {
            assertMatchesReference(
                    randomPixels(random, 9 * 7),
                    9,
                    7,
                    4,
                    3);
        }
    }

    @Test
    void pixelCountMismatchIsRejected() {
        int[] pixels = new int[16 * 16];
        assertThrows(
                IllegalArgumentException.class,
                () -> InitialBlockAtlasResources.analyze(
                        pixels,
                        16,
                        16,
                        16,
                        16,
                        pixels.length - 1));
    }

    private static InitialBlockAtlasResources.PixelAnalysis analyze(
            int[] straightArgb,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight) {
        return InitialBlockAtlasResources.analyze(
                toAbgr(straightArgb),
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                straightArgb.length);
    }

    private static void assertMatchesReference(
            int[] straightArgb,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight) {
        int[] abgr = toAbgr(straightArgb);
        int[] reference = new int[abgr.length];
        for (int index = 0; index < abgr.length; index++) {
            reference[index] = ARGB.fromABGR(abgr[index]);
        }
        InitialBlockAtlasResources.PixelAnalysis analysis =
                InitialBlockAtlasResources.analyze(
                        abgr,
                        sheetWidth,
                        sheetHeight,
                        frameWidth,
                        frameHeight,
                        abgr.length);
        assertArrayEquals(
                reference,
                analysis.straightArgb(),
                "straight ARGB pixels differ from ARGB.fromABGR reference");
        assertArrayEquals(
                reference,
                abgr,
                "analysis must convert the ABGR copy in place");
        assertEquals(
                TexturePixelAnalysis.isOpaque(reference),
                analysis.opaque(),
                "opaque differs from TexturePixelAnalysis");
        assertEquals(
                TexturePixelAnalysis.hasFramedAlpha(
                        sheetWidth,
                        sheetHeight,
                        frameWidth,
                        frameHeight,
                        reference),
                analysis.framedAlpha(),
                "framedAlpha differs from TexturePixelAnalysis");
    }

    private static int[] toAbgr(int[] straightArgb) {
        int[] abgr = new int[straightArgb.length];
        for (int index = 0; index < straightArgb.length; index++) {
            abgr[index] = ARGB.toABGR(straightArgb[index]);
        }
        return abgr;
    }

    private static int[] randomPixels(Random random, int count) {
        int[] pixels = new int[count];
        for (int index = 0; index < count; index++) {
            int mode = random.nextInt(4);
            int alpha = switch (mode) {
                case 0 -> 0xFF;
                case 1 -> 0;
                case 2 -> random.nextInt(256);
                default -> random.nextBoolean()
                        ? 0xFF
                        : random.nextInt(256);
            };
            pixels[index] = alpha << 24 | random.nextInt(1 << 24);
        }
        return pixels;
    }

    private static int[] uniform(int color, int count) {
        int[] pixels = new int[count];
        java.util.Arrays.fill(pixels, color);
        return pixels;
    }

    private static int[] framedRing(
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int borderColor,
            int interiorColor) {
        int[] pixels = new int[sheetWidth * sheetHeight];
        fillFramedRing(
                pixels,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                0,
                0,
                borderColor,
                interiorColor);
        return pixels;
    }

    private static void fillFramedRing(
            int[] pixels,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int originX,
            int originY,
            int borderColor,
            int interiorColor) {
        int borderWidth = Math.max(
                1,
                Math.min(frameWidth, frameHeight) * 3 / 16);
        for (int y = 0; y < frameHeight; y++) {
            for (int x = 0; x < frameWidth; x++) {
                boolean edge = x < borderWidth
                        || x >= frameWidth - borderWidth
                        || y < borderWidth
                        || y >= frameHeight - borderWidth;
                pixels[(originY + y) * sheetWidth + originX + x] =
                        edge ? borderColor : interiorColor;
            }
        }
    }
}
