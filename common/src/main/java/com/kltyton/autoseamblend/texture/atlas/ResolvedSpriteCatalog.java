package com.kltyton.autoseamblend.texture.atlas;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 中文：保存一个根代次中已经缝合并解析的生成精灵。
 * English: Stores generated sprites that were stitched and resolved for one root generation.
 */
public record ResolvedSpriteCatalog(
        long generation,
        Map<String, TextureAtlasSprite[]> spritesByKey) {
    public ResolvedSpriteCatalog {
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be non-negative");
        }
        LinkedHashMap<String, TextureAtlasSprite[]> immutable = new LinkedHashMap<>();
        Objects.requireNonNull(spritesByKey, "spritesByKey")
                .forEach((key, sprites) -> immutable.put(key, sprites.clone()));
        spritesByKey = Collections.unmodifiableMap(immutable);
    }

    public Optional<TextureAtlasSprite[]> sprites(String owner, String key) {
        TextureAtlasSprite[] sprites = spritesByKey.get(
                com.kltyton.autoseamblend.texture.generation.GeneratedSpriteCatalog.catalogKey(
                        owner,
                        key));
        return sprites == null ? Optional.empty() : Optional.of(sprites.clone());
    }

    public static ResolvedSpriteCatalog empty() {
        return new ResolvedSpriteCatalog(0, Map.of());
    }

    public static ResolvedSpriteCatalog empty(long generation) {
        return new ResolvedSpriteCatalog(generation, Map.of());
    }
}
