package com.kltyton.autoseamblend.texture.generation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：把不可变生成定义解析为按槽位连续排列的资源 ID DTO；实际 Atlas 精灵查找留给 Loader。
 *
 * <p>English: Resolves immutable definitions into slot-contiguous resource-ID DTOs; actual Atlas
 * sprite lookup remains Loader-owned.</p>
 */
public final class GeneratedSpriteResolution {
    private GeneratedSpriteResolution() {}

    public static Optional<SlotPlan> resolve(
            GeneratedSpriteDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        int largestSlot = definition.tiles().stream()
                .mapToInt(GeneratedSpriteDefinition.Tile::slot)
                .max()
                .orElse(-1);
        if (largestSlot < 0) {
            return Optional.empty();
        }
        ResourceLocation[] sprites = new ResourceLocation[largestSlot + 1];
        for (GeneratedSpriteDefinition.Tile tile : definition.tiles()) {
            if (sprites[tile.slot()] != null) {
                throw new IllegalStateException(
                        "generated sprite slot is duplicated: "
                                + tile.slot());
            }
            sprites[tile.slot()] = tile.spriteId();
        }
        for (ResourceLocation sprite : sprites) {
            if (sprite == null) {
                return Optional.empty();
            }
        }
        return Optional.of(new SlotPlan(
                definition.owner(),
                definition.key(),
                List.of(sprites)));
    }

    public record SlotPlan(
            String owner,
            String key,
            List<ResourceLocation> spriteIds) {
        public SlotPlan {
            owner = requireText(owner, "owner");
            key = requireText(key, "key");
            spriteIds = List.copyOf(Objects.requireNonNull(
                    spriteIds,
                    "spriteIds"));
            if (spriteIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "resolved sprite slots must not be empty");
            }
            spriteIds.forEach(value -> Objects.requireNonNull(
                    value,
                    "spriteId"));
        }
    }

    /** 中文：跨代次保存纯资源 ID 槽位，避免 common 持有 Atlas 对象。 / English: Cross-generation resource-ID slot snapshot with no Atlas objects in common. */
    public record Snapshot(
            long generation,
            Map<String, List<ResourceLocation>> spritesByKey) {
        public Snapshot {
            if (generation < 0) {
                throw new IllegalArgumentException(
                        "generation must be non-negative");
            }
            LinkedHashMap<String, List<ResourceLocation>> immutable =
                    new LinkedHashMap<>();
            Objects.requireNonNull(
                            spritesByKey,
                            "spritesByKey")
                    .forEach((key, sprites) -> immutable.put(
                            Objects.requireNonNull(key, "key"),
                            List.copyOf(Objects.requireNonNull(
                                    sprites,
                                    "sprites"))));
            spritesByKey = Collections.unmodifiableMap(immutable);
        }

        public static Snapshot empty() {
            return new Snapshot(0, Map.of());
        }

        public static Snapshot empty(long generation) {
            return new Snapshot(generation, Map.of());
        }
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
}
