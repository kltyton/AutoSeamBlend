package com.kltyton.autoseamblend.compat.continuity.runtime;

import java.util.Objects;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.model.QuadProcessors;

/**
 * 中文：用共享缓存谓词创建 Continuity 原生处理器 holder。
 *
 * English: Creates a native Continuity processor holder with the shared caching predicates.
 */
public final class ContinuityProcessorHolderFactory {
    private ContinuityProcessorHolderFactory() {}

    public static QuadProcessors.ProcessorHolder create(QuadProcessor processor) {
        return new QuadProcessors.ProcessorHolder(
                Objects.requireNonNull(processor, "processor"),
                ContinuityCachingPredicates.INSTANCE);
    }
}
