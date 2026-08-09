package com.kltyton.autoseamblend.texture.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PremultipliedArgbTest {
    @Test
    void convertsBetweenStraightAndPremultipliedArgb() {
        int premultiplied = PremultipliedArgb.fromStraight(0x80FF8040);

        assertEquals(0x80804020, premultiplied);
        assertEquals(0x80FF8040, PremultipliedArgb.toStraight(premultiplied));
    }

    @Test
    void appliesCoverageBeforeSourceOverCompositing() {
        int red = PremultipliedArgb.fromStraight(0xFFFF0000);
        int blue = PremultipliedArgb.fromStraight(0xFF0000FF);
        int halfRed = PremultipliedArgb.applyCoverage(red, 128);

        assertEquals(0xFF80007F, PremultipliedArgb.sourceOver(halfRed, blue));
    }

    @Test
    void rejectsStraightArgbWherePremultipliedInputIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> PremultipliedArgb.toStraight(0x80FF0000));
        assertThrows(IllegalArgumentException.class, () -> PremultipliedArgb.applyCoverage(0x80FF0000, 255));
        assertThrows(IllegalArgumentException.class, () -> PremultipliedArgb.sourceOver(0x80FF0000, 0));
    }
}
