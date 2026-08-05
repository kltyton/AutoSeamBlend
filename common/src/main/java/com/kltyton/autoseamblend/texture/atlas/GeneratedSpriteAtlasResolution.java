package com.kltyton.autoseamblend.texture.atlas;

import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/** 中文：把生成定义解析为已经缝合进方块 Atlas 的精灵目录。 / English: Resolves generated definitions into a catalog of sprites already stitched into the block atlas. */
public final class GeneratedSpriteAtlasResolution {
    private GeneratedSpriteAtlasResolution() {}

    public static ResolvedSpriteCatalog resolve(
            TextureAtlas atlas,
            GeneratedSpriteSetCatalog.Snapshot catalog) {
        Objects.requireNonNull(atlas, "atlas");
        Objects.requireNonNull(catalog, "catalog");
        LinkedHashMap<String, TextureAtlasSprite[]> spritesByKey =
                new LinkedHashMap<>();
        catalog.definitions()
                .values()
                .forEach(definition -> resolveSet(
                        atlas,
                        definition)
                        .ifPresent(sprites -> spritesByKey.put(
                                catalogKey(
                                        definition.owner(),
                                        definition.key()),
                                sprites)));
        return new ResolvedSpriteCatalog(
                catalog.generation(),
                spritesByKey);
    }

    public static Optional<TextureAtlasSprite[]> sprites(
            String owner,
            String key) {
        return sprites(
                ReloadPublication.current(),
                owner,
                key);
    }

    /** 中文：从调用方捕获的同一根代次读取生成精灵。 / English: Reads generated sprites from the same root generation captured by the caller. */
    public static Optional<TextureAtlasSprite[]> sprites(
            ReloadPublication.Generation generation,
            String owner,
            String key) {
        Objects.requireNonNull(generation, "generation");
        return generation.resolvedSprites().sprites(owner, key);
    }

    private static Optional<TextureAtlasSprite[]> resolveSet(
            TextureAtlas atlas,
            GeneratedSpriteSet definition) {
        Optional<com.kltyton.autoseamblend.texture.generation.GeneratedSpriteResolution.SlotPlan>
                slotPlan = com.kltyton.autoseamblend.texture.generation.GeneratedSpriteResolution
                        .resolve(definition.definition());
        if (slotPlan.isEmpty()) {
            return Optional.empty();
        }
        List<Identifier> spriteIds =
                slotPlan.orElseThrow().spriteIds();
        TextureAtlasSprite[] sprites =
                new TextureAtlasSprite[spriteIds.size()];
        for (int slot = 0; slot < spriteIds.size(); slot++) {
            Identifier spriteId = spriteIds.get(slot);
            TextureAtlasSprite sprite = atlas.getSprite(spriteId);
            if (!spriteId.equals(sprite.contents().name())) {
                return Optional.empty();
            }
            sprites[slot] = sprite;
        }
        return Optional.of(sprites);
    }

    private static String catalogKey(
            String owner,
            String key) {
        return com.kltyton.autoseamblend.texture.generation.GeneratedSpriteCatalog
                .catalogKey(owner, key);
    }
}
