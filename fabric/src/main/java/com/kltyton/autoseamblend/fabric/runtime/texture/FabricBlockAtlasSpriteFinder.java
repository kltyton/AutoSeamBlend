package com.kltyton.autoseamblend.fabric.runtime.texture;

import com.kltyton.autoseamblend.fabric.mixin.TextureAtlasAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.fabricmc.fabric.impl.client.renderer.SpriteFinderImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/**
 * 中文：按方块 Atlas 的对象身份缓存 SpriteFinder；资源重载替换 Atlas 后自动重建。
 * English: Caches the SpriteFinder keyed by the block-atlas object identity and
 * rebuilds it automatically when a resource reload replaces the atlas.
 */
public final class FabricBlockAtlasSpriteFinder {
    private static volatile Snapshot snapshot;

    private FabricBlockAtlasSpriteFinder() {}

    /**
     * 中文：返回当前方块 Atlas 对应的 SpriteFinder，未命中或 Atlas 已更换时按当前
     * 缝合结果重建。
     *
     * English: Returns the SpriteFinder for the current block atlas, rebuilding it
     * from the latest stitch result when absent or when the atlas instance changed.
     */
    public static SpriteFinder current() {
        TextureAtlas atlas = Minecraft.getInstance()
                .getAtlasManager()
                .getAtlasOrThrow(
                        AtlasIds.BLOCKS);
        Snapshot cached = snapshot;
        if (cached == null
                || cached.atlas() != atlas) {
            synchronized (FabricBlockAtlasSpriteFinder.class) {
                cached = snapshot;
                if (cached == null
                        || cached.atlas() != atlas) {
                    cached = new Snapshot(atlas);
                    snapshot = cached;
                }
            }
        }
        return cached.finder();
    }

    private record Snapshot(
            TextureAtlas atlas,
            SpriteFinder finder) {
        private Snapshot(TextureAtlas atlas) {
            this(
                    atlas,
                    createFinder(atlas));
        }
    }

    private static SpriteFinder createFinder(
            TextureAtlas atlas) {
        Map<Identifier, TextureAtlasSprite> textures =
                ((TextureAtlasAccessor) (Object) atlas)
                        .autoseamblend$texturesByName();
        return new SpriteFinderImpl(
                new LinkedHashMap<>(textures),
                atlas.missingSprite());
    }
}
