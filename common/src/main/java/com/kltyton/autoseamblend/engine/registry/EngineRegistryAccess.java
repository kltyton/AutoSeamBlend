package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 中文：统一 Loader 注册表的发现查询、一次性初始化与只读访问。
 *
 * English: Shared discovery, one-time initialization, and read access for a loader registry.
 */
public final class EngineRegistryAccess<T> {
    private final EngineDefinitionCatalog definitions;
    private final EngineDiscovery discovery;
    private final EngineRegistryInitialization<T> initialization;

    public EngineRegistryAccess(
            EngineDefinitionCatalog definitions,
            EngineDiscovery discovery,
            Supplier<EngineRegistrySnapshot> registrySupplier,
            Function<EngineRegistrySnapshot, T> stateFactory) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        initialization = new EngineRegistryInitialization<>(
                Objects.requireNonNull(registrySupplier, "registrySupplier"),
                Objects.requireNonNull(stateFactory, "stateFactory"));
    }

    public List<String> linkableEngineIds() {
        return definitions.linkableEngineIds(discovery);
    }

    public T initialize() {
        return initialization.initialize();
    }

    public T current() {
        return initialization.current();
    }

    public boolean initialized() {
        return initialization.initialized();
    }

    public EngineDescriptor descriptor(String engineId) {
        return definitions.require(engineId).descriptor();
    }

    public EngineFamily family(String engineId) {
        return descriptor(engineId).family();
    }
}
