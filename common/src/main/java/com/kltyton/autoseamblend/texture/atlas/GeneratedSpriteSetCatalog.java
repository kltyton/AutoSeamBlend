package com.kltyton.autoseamblend.texture.atlas;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** 中文：为一个资源重载代次原子发布的状态精灵定义。 / English: Atomically published state-sprite definitions for one resource-reload generation. */
public final class GeneratedSpriteSetCatalog {
    private static final AtomicLong PREPARED_GENERATION =
            new AtomicLong(-1);

    private GeneratedSpriteSetCatalog() {}

    /** 中文：合并全部引擎所有者并构造一个候选目录，不逐所有者发布。 / English: Merges every engine owner into one candidate catalog without per-owner publication. */
    public static Snapshot prepare(
            List<GeneratedSpriteSet> definitions,
            long generation) {
        Objects.requireNonNull(definitions, "definitions");
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "generation must be positive");
        }
        List<GeneratedSpriteSet> ordered = definitions.stream()
                .map(value -> Objects.requireNonNull(value, "definition"))
                .sorted((left, right) -> {
                    int owner = left.owner().compareTo(right.owner());
                    return owner != 0
                            ? owner
                            : left.key().compareTo(right.key());
                })
                .toList();
        com.kltyton.autoseamblend.texture.generation.GeneratedSpriteCatalog.Snapshot
                common = com.kltyton.autoseamblend.texture.generation.GeneratedSpriteCatalog
                        .prepare(
                                ordered.stream()
                                        .map(GeneratedSpriteSet::definition)
                                        .toList(),
                                generation);
        LinkedHashMap<String, GeneratedSpriteSet> adapters =
                new LinkedHashMap<>();
        for (GeneratedSpriteSet definition : ordered) {
            adapters.putIfAbsent(
                    com.kltyton.autoseamblend.texture.generation.GeneratedSpriteCatalog
                            .catalogKey(
                                    definition.owner(),
                                    definition.key()),
                    definition);
        }
        LinkedHashMap<String, GeneratedSpriteSet> byKey =
                new LinkedHashMap<>();
        common.definitions().keySet().forEach(key -> {
            GeneratedSpriteSet definition = adapters.get(key);
            if (definition == null) {
                throw new IllegalStateException(
                        "generated sprite adapter missing " + key);
            }
            byKey.put(key, definition);
        });
        Map<String, GeneratedSpriteSet> nextDefinitions =
                Collections.unmodifiableMap(byKey);
        return new Snapshot(
                generation,
                nextDefinitions);
    }

    public static void markPrepared(long generation) {
        PREPARED_GENERATION.accumulateAndGet(
                generation,
                Math::max);
    }

    public static boolean isPrepared(long generation) {
        return PREPARED_GENERATION.get()
                >= generation;
    }

    public record Snapshot(
            long generation,
            Map<String, GeneratedSpriteSet> definitions) {
        public Snapshot {
            if (generation < 0) {
                throw new IllegalArgumentException(
                        "generation must be non-negative");
            }
            definitions = Collections.unmodifiableMap(
                    new LinkedHashMap<>(
                            Objects.requireNonNull(
                                    definitions,
                                    "definitions")));
        }

        public static Snapshot empty() {
            return new Snapshot(0, Map.of());
        }

        public static Snapshot empty(
                long generation) {
            return new Snapshot(
                    generation,
                    Map.of());
        }
    }
}
