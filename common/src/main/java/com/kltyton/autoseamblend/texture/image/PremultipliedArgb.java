package com.kltyton.autoseamblend.texture.image;

/** 中文：整数预乘 ARGB 转换和 source-over 运算。 / English: Integer premultiplied-ARGB conversion and source-over arithmetic. */
public final class PremultipliedArgb {
    private PremultipliedArgb() {
    }

    public static int fromStraight(int straightArgb) {
        int alpha = alpha(straightArgb);
        return pack(
                alpha,
                multiply(red(straightArgb), alpha),
                multiply(green(straightArgb), alpha),
                multiply(blue(straightArgb), alpha));
    }

    public static int toStraight(int premultipliedArgb) {
        requireValid(premultipliedArgb);
        int alpha = alpha(premultipliedArgb);
        if (alpha == 0) {
            return 0;
        }
        return pack(
                alpha,
                unpremultiply(red(premultipliedArgb), alpha),
                unpremultiply(green(premultipliedArgb), alpha),
                unpremultiply(blue(premultipliedArgb), alpha));
    }

    public static int applyCoverage(int premultipliedArgb, int coverage) {
        requireValid(premultipliedArgb);
        checkByte(coverage, "coverage");
        return pack(
                multiply(alpha(premultipliedArgb), coverage),
                multiply(red(premultipliedArgb), coverage),
                multiply(green(premultipliedArgb), coverage),
                multiply(blue(premultipliedArgb), coverage));
    }

    public static int sourceOver(int source, int destination) {
        requireValid(source);
        requireValid(destination);
        int remaining = 255 - alpha(source);
        return pack(
                alpha(source) + multiply(alpha(destination), remaining),
                red(source) + multiply(red(destination), remaining),
                green(source) + multiply(green(destination), remaining),
                blue(source) + multiply(blue(destination), remaining));
    }

    public static void requireValid(int argb) {
        int alpha = alpha(argb);
        if (red(argb) > alpha || green(argb) > alpha || blue(argb) > alpha) {
            throw new IllegalArgumentException(
                    "RGB channels must not exceed alpha in premultiplied ARGB: 0x"
                            + Integer.toHexString(argb));
        }
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    private static int pack(int alpha, int red, int green, int blue) {
        checkByte(alpha, "alpha");
        checkByte(red, "red");
        checkByte(green, "green");
        checkByte(blue, "blue");
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int multiply(int value, int scale) {
        return (value * scale + 127) / 255;
    }

    private static int unpremultiply(int value, int alpha) {
        return Math.min(255, (value * 255 + alpha / 2) / alpha);
    }

    private static void checkByte(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be in [0, 255]: " + value);
        }
    }
}
