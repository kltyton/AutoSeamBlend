package com.kltyton.autoseamblend.compat.continuity.runtime;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 中文：保存一次 Continuity 探针调用期间的 identity-keyed 处理数据，并集中清理生命周期。
 * English: Stores identity-keyed processing data for one Continuity probe call and centralizes its lifecycle cleanup.
 */
public final class ContinuityProcessingDataState<K> {
    private final IdentityHashMap<K, Object> values = new IdentityHashMap<>();

    /**
     * 中文：按原生 ProcessingDataKey 的 identity 缓存值；首次访问才执行 supplier。
     * English: Caches a value by native ProcessingDataKey identity and invokes the supplier only on first access.
     */
    public <T> T get(K key, Supplier<? extends T> supplier) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");
        @SuppressWarnings("unchecked")
        T value = (T) values.computeIfAbsent(key, ignored -> supplier.get());
        return value;
    }

    /**
     * 中文：按插入前的 key/value 对调用 Loader 提供的原生 reset 动作，再清空本次状态。
     * English: Invokes the Loader-provided native reset action for each key/value pair, then clears this call's state.
     */
    public void reset(BiConsumer<? super K, Object> resetter) {
        Objects.requireNonNull(resetter, "resetter");
        for (Map.Entry<K, Object> entry : values.entrySet()) {
            resetter.accept(entry.getKey(), entry.getValue());
        }
        values.clear();
    }
}
