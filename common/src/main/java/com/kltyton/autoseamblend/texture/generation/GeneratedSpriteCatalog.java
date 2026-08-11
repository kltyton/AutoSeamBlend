package com.kltyton.autoseamblend.texture.generation;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：对生成精灵定义执行确定性排序、键冲突和 Atlas 资源 ID 冲突校验。
 *
 * <p>English: Deterministically orders generated-sprite definitions and validates catalog-key and
 * atlas-resource collisions.</p>
 */
public final class GeneratedSpriteCatalog {
    private GeneratedSpriteCatalog() {}

    public static Snapshot prepare(
            List<GeneratedSpriteDefinition> definitions,
            long generation) {
        Objects.requireNonNull(definitions, "definitions");
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "generation must be positive");
        }
        List<GeneratedSpriteDefinition> ordered = definitions.stream()
                .map(value -> Objects.requireNonNull(value, "definition"))
                .sorted(Comparator
                        .comparing(GeneratedSpriteDefinition::owner)
                        .thenComparing(GeneratedSpriteDefinition::key))
                .toList();
        LinkedHashMap<String, GeneratedSpriteDefinition> byKey =
                new LinkedHashMap<>();
        HashMap<ResourceLocation, String> spriteOwners = new HashMap<>();
        for (GeneratedSpriteDefinition definition : ordered) {
            String catalogKey = catalogKey(
                    definition.owner(),
                    definition.key());
            GeneratedSpriteDefinition previous = byKey.putIfAbsent(
                    catalogKey,
                    definition);
            if (previous != null && !previous.equals(definition)) {
                throw new IllegalStateException(
                        "conflicting generated sprite set " + catalogKey);
            }
            for (GeneratedSpriteDefinition.Tile tile : definition.tiles()) {
                String previousOwner = spriteOwners.putIfAbsent(
                        tile.spriteId(),
                        catalogKey);
                if (previousOwner != null
                        && !previousOwner.equals(catalogKey)) {
                    throw new IllegalStateException(
                            "generated sprite id collision "
                                    + tile.spriteId()
                                    + " between "
                                    + previousOwner
                                    + " and "
                                    + catalogKey);
                }
            }
        }
        return new Snapshot(
                generation,
                Collections.unmodifiableMap(byKey));
    }

    public static String catalogKey(
            String owner,
            String key) {
        return requireText(owner, "owner")
                + ':'
                + requireText(key, "key");
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

    public record Snapshot(
            long generation,
            Map<String, GeneratedSpriteDefinition> definitions) {
        public Snapshot {
            if (generation < 0) {
                throw new IllegalArgumentException(
                        "generation must be non-negative");
            }
            definitions = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(
                            definitions,
                            "definitions")));
        }

        public static Snapshot empty() {
            return new Snapshot(0, Map.of());
        }

        public static Snapshot empty(long generation) {
            return new Snapshot(generation, Map.of());
        }
    }
}
