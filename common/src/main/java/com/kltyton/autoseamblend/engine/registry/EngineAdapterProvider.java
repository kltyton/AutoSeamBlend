package com.kltyton.autoseamblend.engine.registry;

/**
 * 中文：验证阶段的适配器/能力桥；实现可以隔离 Loader 和第三方类型。
 * English: Adapter and capability bridge used during validation; implementations may isolate
 * loader and third-party types.
 */
@FunctionalInterface
public interface EngineAdapterProvider {
    EngineAdapterProvision provide(EngineDefinition definition);
}
