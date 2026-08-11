package com.kltyton.autoseamblend.texture.io;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import javax.imageio.ImageIO;

/** 中文：编码不可变直通 ARGB 快照，且不接触文件系统。 / English: Encodes an immutable straight-ARGB snapshot without touching the filesystem. */
public final class StraightArgbPngEncoder {
    private StraightArgbPngEncoder() {}

    public static byte[] encode(
            int width,
            int height,
            int[] straightArgb) throws IOException {
        Objects.requireNonNull(straightArgb, "straightArgb");
        if (width <= 0
                || height <= 0
                || (long) width * height
                        != straightArgb.length) {
            throw new IllegalArgumentException(
                    "invalid PNG dimensions "
                            + width + 'x' + height
                            + " for "
                            + straightArgb.length
                            + " pixels");
        }
        BufferedImage image = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB);
        image.setRGB(
                0,
                0,
                width,
                height,
                straightArgb,
                0,
                width);
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException(
                    "PNG encoder is unavailable");
        }
        return output.toByteArray();
    }
}
