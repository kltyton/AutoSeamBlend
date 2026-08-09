package com.kltyton.autoseamblend.frontend.uilib.layout.paint;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * 中文：静态 RGBA 输入框与 ARGB 像素之间的唯一转换规则。
 *
 * English: The single conversion rule between static RGBA text input and ARGB
 * pixels.
 */
public final class PaintColorCodec {
    private PaintColorCodec() {}

    public static OptionalInt parseRgba(String value) {
        String normalized = value == null
                ? ""
                : value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.matches("[0-9A-Fa-f]{8}")) {
            return OptionalInt.empty();
        }
        long rgba = Long.parseUnsignedLong(normalized, 16);
        int red = (int) (rgba >>> 24) & 0xFF;
        int green = (int) (rgba >>> 16) & 0xFF;
        int blue = (int) (rgba >>> 8) & 0xFF;
        int alpha = (int) rgba & 0xFF;
        return OptionalInt.of(
                alpha << 24
                        | red << 16
                        | green << 8
                        | blue);
    }

    public static String rgba(int argb) {
        return String.format(
                Locale.ROOT,
                "#%02X%02X%02X%02X",
                (argb >>> 16) & 0xFF,
                (argb >>> 8) & 0xFF,
                argb & 0xFF,
                (argb >>> 24) & 0xFF);
    }
}
