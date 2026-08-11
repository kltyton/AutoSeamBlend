package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：测试共享的归一化 0..1 UV 精灵；getU0/getV0=0、getU1/getV1=1，与已验收 NeoForge
 * TopUvContractTest 同型。
 *
 * <p>English: Shared test sprites with normalized 0..1 UVs (getU0/getV0=0 and
 * getU1/getV1=1), the same shape as the accepted NeoForge TopUvContractTest.
 */
final class PaneTestSprites {
    private PaneTestSprites() {}

    /** 中文：cap/edge 精灵。 / English: The cap/edge sprite. */
    static final TextureAtlasSprite EDGE =
            create("minecraft:block/glass_pane_top", false);

    /** 中文：body 精灵。 / English: The body sprite. */
    static final TextureAtlasSprite BODY =
            create("minecraft:block/glass_pane", true);

    static TextureAtlasSprite create(
            String name,
            boolean opaque) {
        NativeImage image =
                new NativeImage(16, 16, false);
        if (opaque) {
            image.fillRect(
                    0,
                    0,
                    16,
                    16,
                    0xFF000000);
        }
        SpriteContents contents = new SpriteContents(
                new ResourceLocation(name),
                new FrameSize(16, 16),
                image,
                AnimationMetadataSection.EMPTY);
        return new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                contents);
    }

    private static final class TestSprite
            extends TextureAtlasSprite {
        private TestSprite(
                ResourceLocation atlasLocation,
                SpriteContents contents) {
            super(
                    atlasLocation,
                    contents,
                    16,
                    16,
                    0,
                    0);
        }
    }
}
