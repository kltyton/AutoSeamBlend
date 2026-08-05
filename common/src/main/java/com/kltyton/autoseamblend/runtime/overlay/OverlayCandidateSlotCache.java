package com.kltyton.autoseamblend.runtime.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * 中文：按引擎家族和面方向分槽的并发 overlay 候选缓存；快照身份由 Loader 外层原子轮换。
 * English: Concurrent overlay candidate cache partitioned by engine family and face direction;
 * Loader-owned snapshot identity rotates the outer cache atomically.
 */
public final class OverlayCandidateSlotCache<K, V> {
    private final int directionCount;
    private final List<ConcurrentMap<K, Optional<V>>> slots;

    public OverlayCandidateSlotCache(int familyCount, int directionCount) {
        if (familyCount <= 0 || directionCount <= 0) {
            throw new IllegalArgumentException("overlay cache dimensions must be positive");
        }
        this.directionCount = directionCount;
        int slotCount = Math.multiplyExact(familyCount, directionCount);
        ArrayList<ConcurrentMap<K, Optional<V>>> tables = new ArrayList<>(slotCount);
        for (int index = 0; index < slotCount; index++) {
            tables.add(new ConcurrentHashMap<>());
        }
        slots = List.copyOf(tables);
    }

    /**
     * 中文：解析并缓存一个家族/方向槽位；Optional.empty 也会被缓存以避免热路径重复查询。
     * English: Resolves and caches one family/face slot; Optional.empty is cached to avoid
     * repeating misses on the hot path.
     */
    public Optional<V> resolve(
            K key,
            int familyOrdinal,
            int directionOrdinal,
            Function<? super K, Optional<V>> resolver) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resolver, "resolver");
        int slot = slotIndex(familyOrdinal, directionOrdinal);
        return slots.get(slot).computeIfAbsent(key, value ->
                Objects.requireNonNull(resolver.apply(value), "resolver result"));
    }

    private int slotIndex(int familyOrdinal, int directionOrdinal) {
        if (familyOrdinal < 0
                || directionOrdinal < 0
                || directionOrdinal >= directionCount) {
            throw new IndexOutOfBoundsException(
                    "overlay cache slot " + familyOrdinal + ':' + directionOrdinal);
        }
        int slot = Math.addExact(
                Math.multiplyExact(familyOrdinal, directionCount), directionOrdinal);
        if (slot >= slots.size()) {
            throw new IndexOutOfBoundsException("overlay cache family " + familyOrdinal);
        }
        return slot;
    }
}
