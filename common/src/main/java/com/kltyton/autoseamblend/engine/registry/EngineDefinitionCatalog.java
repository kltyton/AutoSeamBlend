package com.kltyton.autoseamblend.engine.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：按格式家族稳定顺序保存候选描述，并提供共享的链接发现视图。
 * English: Stores candidate definitions in stable family order and exposes the shared linkage
 * discovery view.
 */
public final class EngineDefinitionCatalog {
    private static final Comparator<EngineDefinition> STABLE_ORDER =
            Comparator.comparingInt((EngineDefinition definition) ->
                            definition.descriptor().family().stableOrder())
                    .thenComparing(definition -> definition.descriptor().engineId());

    private final List<EngineDefinition> definitions;
    private final Map<String, EngineDefinition> byId;

    private EngineDefinitionCatalog(List<EngineDefinition> definitions) {
        ArrayList<EngineDefinition> ordered = new ArrayList<>(definitions);
        ordered.sort(STABLE_ORDER);
        LinkedHashMap<String, EngineDefinition> index = new LinkedHashMap<>();
        for (EngineDefinition definition : ordered) {
            String engineId = definition.descriptor().engineId();
            if (index.putIfAbsent(engineId, definition) != null) {
                throw new IllegalArgumentException("duplicate engine id: " + engineId);
            }
        }
        this.definitions = List.copyOf(ordered);
        this.byId = Collections.unmodifiableMap(index);
    }

    public static EngineDefinitionCatalog of(List<EngineDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        return new EngineDefinitionCatalog(List.copyOf(definitions));
    }

    public List<EngineDefinition> definitions() {
        return definitions;
    }

    public boolean contains(String engineId) {
        return byId.containsKey(engineId);
    }

    public EngineDefinition require(String engineId) {
        EngineDefinition definition = byId.get(engineId);
        if (definition == null) {
            throw new IllegalArgumentException("unknown engine id: " + engineId);
        }
        return definition;
    }

    public List<String> linkableEngineIds(EngineDiscovery discovery) {
        Objects.requireNonNull(discovery, "discovery");
        return definitions.stream()
                .filter(definition -> discovery.installedVersion(
                                definition.descriptor().modId())
                        .filter(definition.descriptor().expectedVersion()::equals)
                        .isPresent())
                .filter(definition -> definition.hooks().stream()
                        .allMatch(discovery::hookPresent))
                .map(definition -> definition.descriptor().engineId())
                .toList();
    }
}
