package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.EngineAdapter;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：Loader 无关的引擎注册表运行态；启动时冻结就绪适配器，避免逐 quad 重建列表。
 * English: Loader-neutral runtime engine-registry state. Ready adapters are frozen at startup
 * so render-time queries do not rebuild their lists per quad.
 */
public final class EngineRegistryRuntimeState {
    private final EngineRegistrySnapshot registry;
    private final EngineSelection selection;
    private final List<String> readyEngineIds;
    private final List<EngineAdapter> readyAdapters;
    private final Map<String, EngineFamily> readyFamilies;

    public EngineRegistryRuntimeState(
            EngineRegistrySnapshot registry,
            EngineSelection selection) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.readyAdapters = registry.readyAdapters();

        LinkedHashMap<String, EngineFamily> families = new LinkedHashMap<>();
        for (EngineAdapter adapter : readyAdapters) {
            families.put(adapter.descriptor().engineId(), adapter.descriptor().family());
        }
        this.readyFamilies = Map.copyOf(families);
        this.readyEngineIds = List.copyOf(families.keySet());
    }

    public EngineRegistrySnapshot registry() {
        return registry;
    }

    public EngineSelection selection() {
        return selection;
    }

    public List<String> readyEngineIds() {
        return readyEngineIds;
    }

    public List<EngineAdapter> readyAdapters() {
        return readyAdapters;
    }

    public boolean engineRequired() {
        return readyAdapters.isEmpty();
    }

    public EngineFamily family(String engineId) {
        Objects.requireNonNull(engineId, "engineId");
        EngineFamily family = readyFamilies.get(engineId);
        if (family == null) {
            throw new IllegalArgumentException("unknown ready engine: " + engineId);
        }
        return family;
    }
}
