package com.kltyton.autoseamblend.engine.plan;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.query.ResolutionKey;
import com.kltyton.autoseamblend.selection.method.ConfiguredMethodPlan;
import java.util.Objects;

/** 中文：运行时、预览、实体化和 baked 导出共享的唯一已解析计划。 / English: Single resolved plan shared by runtime, preview, materialize and baked export. */
public record EngineExecutionPlan(
        ResolutionKey resolutionKey,
        EngineDescriptor engine,
        ConfiguredMethodPlan method,
        NativeMethodMapping mapping,
        CompletionPlan completion) {
    public EngineExecutionPlan {
        Objects.requireNonNull(resolutionKey, "resolutionKey");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(completion, "completion");
        if (mapping.method() != method.resolvedMethod()) {
            throw new IllegalArgumentException("mapping and resolved method must agree");
        }
        if (completion.method() != method) {
            throw new IllegalArgumentException("execution and completion must share one method plan instance");
        }
        if (!engine.engineId().equals(resolutionKey.engineId())) {
            throw new IllegalArgumentException("execution engine and resolution key must agree");
        }
    }
}
