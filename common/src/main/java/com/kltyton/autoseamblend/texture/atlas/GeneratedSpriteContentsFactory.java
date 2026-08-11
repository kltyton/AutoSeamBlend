package com.kltyton.autoseamblend.texture.atlas;

import com.kltyton.autoseamblend.texture.io.NativeArgb;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/**
 * 中文：把公共生成像素转成由 {@link SpriteContents} 接管所有权的原生图像。
 *
 * <p>English: Converts shared generated pixels into a native image whose ownership is transferred
 * to {@link SpriteContents}.
 */
public final class GeneratedSpriteContentsFactory {
    private GeneratedSpriteContentsFactory() {
    }

    public static SpriteContents create(
            ResourceLocation spriteId,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int[] straightArgb,
            Optional<AnimationMetadataSection> animation,
            Optional<TextureMetadataSection> texture) {
        Objects.requireNonNull(spriteId, "spriteId");
        Objects.requireNonNull(straightArgb, "straightArgb");
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(texture, "texture");
        if (sheetWidth <= 0 || sheetHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) {
            throw new IllegalArgumentException("sprite dimensions must be positive");
        }
        int expectedPixels = Math.multiplyExact(sheetWidth, sheetHeight);
        if (straightArgb.length != expectedPixels) {
            throw new IllegalArgumentException(
                    "pixel count " + straightArgb.length + " does not match " + expectedPixels);
        }

        NativeImage image = new NativeImage(sheetWidth, sheetHeight, false);
        boolean transferred = false;
        try {
            for (int y = 0; y < sheetHeight; y++) {
                for (int x = 0; x < sheetWidth; x++) {
                    image.setPixelRGBA(
                            x,
                            y,
                            NativeArgb.toNative(
                                    straightArgb[
                                            y * sheetWidth + x]));
                }
            }
            // 1.20.1 SpriteContents has no ResourceMetadata slot; only the animation
            // section is passed (mipmap/texture metadata are derived by the atlas).
            SpriteContents contents = new SpriteContents(
                    spriteId,
                    new FrameSize(frameWidth, frameHeight),
                    image,
                    animation.orElse(AnimationMetadataSection.EMPTY));
            transferred = true;
            return contents;
        } finally {
            if (!transferred) {
                image.close();
            }
        }
    }
}
