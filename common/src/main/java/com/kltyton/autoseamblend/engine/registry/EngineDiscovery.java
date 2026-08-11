package com.kltyton.autoseamblend.engine.registry;

import java.util.Optional;

/**
 * 中文：Loader 发现边界；只返回版本和类资源存在性，不加载可选引擎类型。
 * English: Loader discovery boundary; it reports versions and class-resource presence without
 * loading optional engine types.
 */
public interface EngineDiscovery {
    Optional<String> installedVersion(String modId);

    boolean hookPresent(String resourcePath);
}
