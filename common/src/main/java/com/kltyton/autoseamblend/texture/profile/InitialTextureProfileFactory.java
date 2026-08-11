package com.kltyton.autoseamblend.texture.profile;

import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import com.kltyton.autoseamblend.texture.atlas.AtlasSourceImage;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import java.util.Objects;

/**
 * 中文：从首轮 Atlas 源图像的首帧构造 alpha 框架和 overlay 轮廓。
 *
 * English: Builds alpha frame and overlay profiles from the first frame of an initial Atlas source
 * image.
 */
public final class InitialTextureProfileFactory {
    private InitialTextureProfileFactory() {}

    public static InitialTextureProfiles from(SurfaceSourceSnapshot source) {
        SurfaceSourceSnapshot image = Objects.requireNonNull(source, "source");
        return create(
                image.frameWidth(),
                image.frameHeight(),
                image.sheetWidth(),
                image.straightArgb(),
                image.framedAlpha());
    }

    public static InitialTextureProfiles from(AtlasSourceImage source) {
        AtlasSourceImage image = Objects.requireNonNull(source, "source");
        return create(
                image.frameWidth(),
                image.frameHeight(),
                image.sheetWidth(),
                image.straightArgb(),
                image.framedAlpha());
    }

    public static InitialTextureProfiles create(
            int frameWidth,
            int frameHeight,
            int sheetWidth,
            int[] straightArgb,
            boolean framedAlpha) {
        int[] sheet = Objects.requireNonNull(straightArgb, "straightArgb");
        int[] framePixels = new int[Math.multiplyExact(frameWidth, frameHeight)];
        for (int y = 0; y < frameHeight; y++) {
            System.arraycopy(
                    sheet,
                    y * sheetWidth,
                    framePixels,
                    y * frameWidth,
                    frameWidth);
        }
        TextureFrameProfile frame = TextureFrameProfile.fromAlpha(
                frameWidth,
                frameHeight,
                framedAlpha,
                (x, y) -> (framePixels[y * frameWidth + x] >>> 24) != 0);
        return new InitialTextureProfiles(
                frame,
                OverlayCutoutProfile.fromArgb(
                        frameWidth,
                        frameHeight,
                        framePixels));
    }

}
