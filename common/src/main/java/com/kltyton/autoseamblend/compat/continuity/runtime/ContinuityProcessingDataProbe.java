package com.kltyton.autoseamblend.compat.continuity.runtime;

import java.util.Objects;
import java.util.function.Consumer;
import me.pepperbell.continuity.api.client.ProcessingDataKey;
import me.pepperbell.continuity.api.client.ProcessingDataProvider;

/**
 * 中文：跨 Loader 复用 Continuity 处理谓词的一次查询数据容器。
 *
 * English: Per-query Continuity processing-data container shared across loaders.
 */
public final class ContinuityProcessingDataProbe implements ProcessingDataProvider {
    private final ContinuityProcessingDataState<ProcessingDataKey<?>> values =
            new ContinuityProcessingDataState<>();

    @Override
    public <T> T getData(ProcessingDataKey<T> key) {
        ProcessingDataKey<T> checked = Objects.requireNonNull(key, "key");
        return values.get(checked, checked.getValueSupplier());
    }

    public void reset() {
        values.reset(ContinuityProcessingDataProbe::resetValue);
    }

    @SuppressWarnings("unchecked")
    private static <T> void resetValue(ProcessingDataKey<?> key, Object value) {
        ProcessingDataKey<T> typed = (ProcessingDataKey<T>) key;
        Consumer<T> reset = typed.getValueResetAction();
        if (reset != null) {
            reset.accept((T) value);
        }
    }
}
