package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.texture.image.ArgbImage;
import com.kltyton.autoseamblend.texture.image.PremultipliedArgb;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class Ctm47TileGeneratorTest {
    @Test
    void connectedEdgesUseInteriorPixelsWhileDisconnectedEdgesKeepTheirBorder() {
        int[] pixels = new int[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                pixels[y * 16 + x] = x == 0 || y == 0 || x == 15 || y == 15 ? 0xFFFFFFFF : 0;
            }
        }
        ArgbImage source = ArgbImage.fromStraightArgb(16, 16, pixels);

        ArgbImage unconnected = Ctm47TileGenerator.generate(source, 0, 1);
        assertEquals(0xFFFFFFFF, PremultipliedArgb.toStraight(unconnected.pixelAt(0, 8)));

        ArgbImage connectedAll = Ctm47TileGenerator.generate(source, 26, 1);
        assertEquals(0, PremultipliedArgb.toStraight(connectedAll.pixelAt(0, 8)));
        assertEquals(0, PremultipliedArgb.toStraight(connectedAll.pixelAt(0, 0)));

        ArgbImage connectedUp = Ctm47TileGenerator.generate(source, 36, 1);
        assertEquals(0, PremultipliedArgb.toStraight(connectedUp.pixelAt(8, 0)));
        assertEquals(0xFFFFFFFF, PremultipliedArgb.toStraight(connectedUp.pixelAt(0, 8)));
    }

    @Test
    void fullyCoveredTranslucentTextureKeepsDiagonalHighlights() {
        int body = 0x66993333;
        int detail = 0x9B993333;
        int frame = 0xA3993333;
        int[] pixels = new int[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                pixels[y * 16 + x] = x == 0 || y == 0 || x == 15 || y == 15
                        ? frame
                        : body;
            }
        }
        pixels[2 * 16 + 4] = detail;
        pixels[3 * 16 + 3] = detail;
        pixels[4 * 16 + 2] = detail;
        pixels[12 * 16 + 13] = detail;
        pixels[13 * 16 + 12] = detail;

        ArgbImage connectedAll = Ctm47TileGenerator.generate(
                ArgbImage.fromStraightArgb(16, 16, pixels),
                26,
                3);

        assertEquals(detail, PremultipliedArgb.toStraight(connectedAll.pixelAt(4, 2)));
        assertEquals(detail, PremultipliedArgb.toStraight(connectedAll.pixelAt(3, 3)));
        assertEquals(detail, PremultipliedArgb.toStraight(connectedAll.pixelAt(2, 4)));
        int normalizedBody = PremultipliedArgb.toStraight(
                PremultipliedArgb.fromStraight(body));
        assertEquals(
                normalizedBody,
                PremultipliedArgb.toStraight(
                        connectedAll.pixelAt(0, 8)));
    }
}
