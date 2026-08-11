package com.kltyton.autoseamblend.texture.atlas;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpriteTransform;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpriteDefinition;
import com.kltyton.autoseamblend.texture.io.NativeArgb;
import com.kltyton.autoseamblend.mixin.minecraft.SpriteContentsImageAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：不可变的重载时源像素与原生状态精灵配方；定义只包含已解码像素，绝不编码或写入 PNG 数据。
 *
 * English:
 * Immutable reload-time source pixels and native state-sprite recipes.
 *
 * <p>Definitions contain decoded pixels only. They never encode or write PNG data.
 */
public final class GeneratedSpriteSet {
    private final GeneratedSpriteDefinition definition;
    private final List<Tile> tiles;

    private GeneratedSpriteSet(
            GeneratedSpriteDefinition definition,
            List<Tile> tiles) {
        this.definition = Objects.requireNonNull(
                definition,
                "definition");
        this.tiles = List.copyOf(Objects.requireNonNull(
                tiles,
                "tiles"));
        if (this.tiles.size() != definition.tiles().size()) {
            throw new IllegalArgumentException(
                    "adapter tile count differs from common definition");
        }
    }

    public static GeneratedSpriteSet capture(
            String owner,
            String key,
            SpriteContents contents,
            List<Tile> tiles) {
        Objects.requireNonNull(contents, "contents");
        NativeImage image =
                ((SpriteContentsImageAccessor) contents)
                        .autoseamblend$originalImage();
        int sheetWidth = image.getWidth();
        int sheetHeight = image.getHeight();
        int[] pixels = new int[Math.multiplyExact(
                sheetWidth,
                sheetHeight)];
        for (int y = 0; y < sheetHeight; y++) {
            for (int x = 0; x < sheetWidth; x++) {
                pixels[y * sheetWidth + x] =
                        NativeArgb.toIr(
                                image.getPixelRGBA(x, y));
            }
        }
        return capture(
                owner,
                key,
                contents.name(),
                sheetWidth,
                sheetHeight,
                contents.width(),
                contents.height(),
                pixels,
                tiles);
    }

    public static GeneratedSpriteSet capture(
            String owner,
            String key,
            ResourceLocation sourceSpriteId,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int[] straightArgb,
            List<Tile> tiles) {
        List<Tile> frozenTiles = List.copyOf(Objects.requireNonNull(
                tiles,
                "tiles"));
        return new GeneratedSpriteSet(
                new GeneratedSpriteDefinition(
                        owner,
                        key,
                        sourceSpriteId,
                        sheetWidth,
                        sheetHeight,
                        frameWidth,
                        frameHeight,
                        straightArgb,
                        frozenTiles.stream()
                                .map(Tile::definition)
                                .toList()),
                frozenTiles);
    }

    public String owner() {
        return definition.owner();
    }

    public String key() {
        return definition.key();
    }

    public ResourceLocation sourceSpriteId() {
        return definition.sourceSpriteId();
    }

    public GeneratedSpriteDefinition definition() {
        return definition;
    }

    public List<Tile> tiles() {
        return tiles;
    }

    public SourceMetadata loadSourceMetadata(
            ResourceManager resources) {
        Objects.requireNonNull(resources, "resources");
        ResourceLocation textureFile =
                SpriteSource.TEXTURE_ID_CONVERTER.idToFile(
                sourceSpriteId());
        Optional<Resource> resource =
                resources.getResource(textureFile);
        if (resource.isEmpty()) {
            return SourceMetadata.empty();
        }
        try {
            return new SourceMetadata(
                    resource.orElseThrow()
                            .metadata()
                            .getSection(
                                    AnimationMetadataSection.SERIALIZER),
                    resource.orElseThrow()
                            .metadata()
                            .getSection(
                                    TextureMetadataSection.SERIALIZER)
                            .map(metadata -> new TextureMetadataSection(
                                    metadata.isBlur(),
                                    metadata.isClamp())));
        } catch (IOException exception) {
            Constants.LOG.warn(
                    "Could not read generated-sprite metadata for {}",
                    sourceSpriteId(),
                    exception);
            return SourceMetadata.empty();
        }
    }

    public SpriteContents load(
            Tile tile,
            SourceMetadata metadata) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(metadata, "metadata");
        if (!tiles.contains(tile)) {
            throw new IllegalArgumentException(
                    "tile does not belong to generated sprite set "
                            + key());
        }
        int[] generated = definition.compose(tile.transform());
        return GeneratedSpriteContentsFactory.create(
                tile.spriteId(),
                definition.sheetWidth(),
                definition.sheetHeight(),
                definition.frameWidth(),
                definition.frameHeight(),
                generated,
                metadata.animation(),
                metadata.texture());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GeneratedSpriteSet that)) {
            return false;
        }
        return definition.equals(that.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    public record Tile(
            int slot,
            ResourceLocation spriteId,
            GeneratedSpriteTransform transform) {
        public Tile {
            if (slot < 0) {
                throw new IllegalArgumentException(
                        "slot must be non-negative");
            }
            Objects.requireNonNull(spriteId, "spriteId");
            Objects.requireNonNull(transform, "transform");
        }

        private GeneratedSpriteDefinition.Tile definition() {
            return new GeneratedSpriteDefinition.Tile(
                    slot,
                    spriteId,
                    transform);
        }
    }

    public record SourceMetadata(
            Optional<AnimationMetadataSection> animation,
            Optional<TextureMetadataSection> texture) {
        public SourceMetadata {
            animation = Objects.requireNonNull(
                    animation,
                    "animation");
            texture = Objects.requireNonNull(
                    texture,
                    "texture");
        }

        public static SourceMetadata empty() {
            return new SourceMetadata(
                    Optional.empty(),
                    Optional.empty());
        }
    }
}
