package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.EngineAdapter;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.List;
import java.util.Objects;

/**
 * 中文：Loader 无关的引擎注册表运行态。
 * English: Loader-neutral runtime state of the engine registry.
 */
public record EngineRegistryRuntimeState(
        EngineRegistrySnapshot registry,
        EngineSelection selection) {
    public EngineRegistryRuntimeState {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(selection, "selection");
    }

    public List<String> readyEngineIds() {
        return registry.readyEngineIds();
    }

    public List<EngineAdapter> readyAdapters() {
        return registry.readyAdapters();
    }

    public boolean engineRequired() {
        return registry.engineRequired();
    }

    public EngineFamily family(String engineId) {
        Objects.requireNonNull(engineId, "engineId");
        return readyAdapters().stream()
                .map(EngineAdapter::descriptor)
                .filter(descriptor -> descriptor.engineId().equals(engineId))
                .map(descriptor -> descriptor.family())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown ready engine: " + engineId));
    }
}
