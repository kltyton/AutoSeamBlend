package com.kltyton.autoseamblend.texture;

import com.kltyton.autoseamblend.mixin.minecraft.SpriteContentsImageAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 中文：MC 1.20.1 没有 26.1 的 com.mojang.blaze3d.platform.Transparency；这里从精灵原始像素
 * 推导等价的三态透明度，供渲染层与 CUTOUT/TRANSLUCENT 选择使用。
 *
 * English: MC 1.20.1 lacks 26.1's com.mojang.blaze3d.platform.Transparency; this derives the
 * equivalent three-state transparency from raw sprite pixels for render-layer and
 * CUTOUT/TRANSLUCENT selection.
 */
public enum SpriteTransparency {
    OPAQUE,
    TRANSLUCENT,
    TRANSPARENT;

    public boolean isOpaque() {
        return this == OPAQUE;
    }

    public boolean hasTranslucent() {
        return this == TRANSLUCENT;
    }

    public boolean hasTransparent() {
        return this == TRANSPARENT;
    }

    /**
     * 中文：扫描原始精灵像素；存在半透明像素判定为 TRANSLUCENT，否则存在全透明像素判定为
     * TRANSPARENT，其余为 OPAQUE。
     *
     * English: Scans raw sprite pixels; semi-transparent pixels yield TRANSLUCENT, otherwise
     * fully transparent pixels yield TRANSPARENT, otherwise OPAQUE.
     */
    public static SpriteTransparency of(
            TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        NativeImage image =
                ((SpriteContentsImageAccessor) contents)
                        .autoseamblend$originalImage();
        boolean transparent = false;
        boolean translucent = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getPixelRGBA(x, y) >>> 24;
                if (alpha == 0) {
                    transparent = true;
                } else if (alpha < 255) {
                    translucent = true;
                }
                if (translucent) {
                    return TRANSLUCENT;
                }
            }
        }
        if (transparent) {
            return TRANSPARENT;
        }
        return OPAQUE;
    }
}
