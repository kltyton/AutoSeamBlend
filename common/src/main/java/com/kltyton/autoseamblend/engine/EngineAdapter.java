package com.kltyton.autoseamblend.engine;

import com.kltyton.autoseamblend.engine.capability.CapabilityMatrix;
import com.kltyton.autoseamblend.engine.plan.EngineExecutionPlan;
import com.kltyton.autoseamblend.engine.plan.NativeMethodMapping;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.selection.query.QueryResolution;

/** 中文：不依赖第三方类型的引擎 SPI；实现和原生类型保留在 Loader 的 compat 包中。 / English: Third-party-free engine SPI. Implementations and native types stay in loader compat packages. */
public interface EngineAdapter {
    EngineDescriptor descriptor();

    CapabilityMatrix capabilities();

    QueryObservation observe(ConnectionQuery query, EngineQueryContext nativeContext);

    NativeMethodMapping mapping(com.kltyton.autoseamblend.selection.method.ConnectionMethod method);

    default EngineExecutionPlan plan(QueryResolution resolution) {
        if (!descriptor().engineId().equals(resolution.key().engineId())) {
            throw new IllegalArgumentException("query resolution belongs to another engine");
        }
        return new EngineExecutionPlan(
                resolution.key(),
                descriptor(),
                resolution.method(),
                mapping(resolution.method().resolvedMethod()),
                resolution.completion());
    }
}
