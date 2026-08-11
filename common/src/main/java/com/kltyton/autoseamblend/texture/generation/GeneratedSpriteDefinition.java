package com.kltyton.autoseamblend.texture.generation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：保存一个生成状态精灵集合的纯像素定义，并负责跨动画帧的确定性合成。
 *
 * <p>English: Immutable pixel definition for one generated state-sprite set, including
 * deterministic composition across every animation frame.</p>
 */
public final class GeneratedSpriteDefinition {
    private final String owner;
    private final String key;
    private final ResourceLocation sourceSpriteId;
    private final int sheetWidth;
    private final int sheetHeight;
    private final int frameWidth;
    private final int frameHeight;
    private final int[] sourceStraightArgb;
    private final List<Tile> tiles;

    public GeneratedSpriteDefinition(
            String owner,
            String key,
            ResourceLocation sourceSpriteId,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int[] sourceStraightArgb,
            List<Tile> tiles) {
        this.owner = requireText(owner, "owner");
        this.key = requireText(key, "key");
        this.sourceSpriteId = Objects.requireNonNull(
                sourceSpriteId,
                "sourceSpriteId");
        validateDimensions(
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight);
        int expectedPixels = Math.multiplyExact(
                sheetWidth,
                sheetHeight);
        this.sourceStraightArgb = Objects.requireNonNull(
                        sourceStraightArgb,
                        "sourceStraightArgb")
                .clone();
        if (this.sourceStraightArgb.length != expectedPixels) {
            throw new IllegalArgumentException(
                    "source pixel count differs from sheet dimensions");
        }
        this.tiles = List.copyOf(Objects.requireNonNull(
                tiles,
                "tiles"));
        if (this.tiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "generated sprite set must contain at least one tile");
        }
        Set<Integer> slots = new HashSet<>();
        Set<ResourceLocation> spriteIds = new HashSet<>();
        for (Tile tile : this.tiles) {
            if (!slots.add(tile.slot())) {
                throw new IllegalArgumentException(
                        "generated sprite slots must be unique");
            }
            if (!spriteIds.add(tile.spriteId())) {
                throw new IllegalArgumentException(
                        "generated sprite ids must be unique inside a set");
            }
        }
        this.sheetWidth = sheetWidth;
        this.sheetHeight = sheetHeight;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    public String owner() {
        return owner;
    }

    public String key() {
        return key;
    }

    public ResourceLocation sourceSpriteId() {
        return sourceSpriteId;
    }

    public int sheetWidth() {
        return sheetWidth;
    }

    public int sheetHeight() {
        return sheetHeight;
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public int[] sourceStraightArgb() {
        return sourceStraightArgb.clone();
    }

    public List<Tile> tiles() {
        return tiles;
    }

    /**
     * 中文：对源图的每个动画帧应用同一个值语义变换，保持原始 sheet 排列。
     * English: Applies one value-based transform to every source animation frame while preserving
     * the original sheet arrangement.
     */
    public int[] compose(GeneratedSpriteTransform transform) {
        Objects.requireNonNull(transform, "transform");
        int columns = sheetWidth / frameWidth;
        int rows = sheetHeight / frameHeight;
        int[] composed = new int[sourceStraightArgb.length];
        int[] sourceFrame = new int[Math.multiplyExact(
                frameWidth,
                frameHeight)];
        for (int frameY = 0; frameY < rows; frameY++) {
            for (int frameX = 0; frameX < columns; frameX++) {
                int originX = frameX * frameWidth;
                int originY = frameY * frameHeight;
                for (int y = 0; y < frameHeight; y++) {
                    System.arraycopy(
                            sourceStraightArgb,
                            (originY + y) * sheetWidth + originX,
                            sourceFrame,
                            y * frameWidth,
                            frameWidth);
                }
                int[] generated = Objects.requireNonNull(
                        transform.materialize(
                                frameWidth,
                                frameHeight,
                                sourceFrame),
                        "generated pixels");
                if (generated.length != sourceFrame.length) {
                    throw new IllegalArgumentException(
                            "generated pixel count differs from frame dimensions");
                }
                for (int y = 0; y < frameHeight; y++) {
                    System.arraycopy(
                            generated,
                            y * frameWidth,
                            composed,
                            (originY + y) * sheetWidth + originX,
                            frameWidth);
                }
            }
        }
        return composed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GeneratedSpriteDefinition that)) {
            return false;
        }
        return sheetWidth == that.sheetWidth
                && sheetHeight == that.sheetHeight
                && frameWidth == that.frameWidth
                && frameHeight == that.frameHeight
                && owner.equals(that.owner)
                && key.equals(that.key)
                && sourceSpriteId.equals(that.sourceSpriteId)
                && tiles.equals(that.tiles)
                && Arrays.equals(sourceStraightArgb, that.sourceStraightArgb);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                owner,
                key,
                sourceSpriteId,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                tiles);
        return 31 * result + Arrays.hashCode(sourceStraightArgb);
    }

    private static void validateDimensions(
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight) {
        if (sheetWidth <= 0
                || sheetHeight <= 0
                || frameWidth <= 0
                || frameHeight <= 0
                || sheetWidth % frameWidth != 0
                || sheetHeight % frameHeight != 0) {
            throw new IllegalArgumentException(
                    "invalid generated sprite sheet dimensions");
        }
        Math.multiplyExact(sheetWidth, sheetHeight);
    }

    private static String requireText(
            String value,
            String name) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " must not be blank");
        }
        return value;
    }

    /** 中文：一个生成槽位的稳定资源 ID 与像素变换。 / English: Stable resource ID and pixel transform for one generated slot. */
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
    }
}
