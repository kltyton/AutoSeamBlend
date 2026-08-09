package com.kltyton.autoseamblend.config.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：Loader 从持久化配置冻结出的有序、不可变项目 DTO。
 *
 * English: Ordered immutable project DTO frozen by a loader from persistent
 * configuration state.
 */
public record ConfigSnapshot(
        boolean automaticDiscovery,
        Map<String, Map<String, List<String>>> targets,
        Map<String, Map<String, List<String>>> excludedTargets) {
    public ConfigSnapshot {
        targets = immutableSelectorMap(targets);
        excludedTargets = immutableSelectorMap(excludedTargets);
    }

    public static ConfigSnapshot capture(
            boolean automaticDiscovery,
            Map<String, ? extends Map<String, ? extends List<String>>> targets,
            Map<String, ? extends Map<String, ? extends List<String>>> excludedTargets) {
        return new ConfigSnapshot(
                automaticDiscovery,
                immutableSelectorMap(targets),
                immutableSelectorMap(excludedTargets));
    }

    private static Map<String, Map<String, List<String>>> immutableSelectorMap(
            Map<String, ? extends Map<String, ? extends List<String>>> source) {
        Objects.requireNonNull(source, "source");
        LinkedHashMap<String, Map<String, List<String>>> methods = new LinkedHashMap<>();
        source.forEach((method, modes) -> {
            if (method == null || method.isBlank()) {
                throw new IllegalArgumentException("configuration method must not be blank");
            }
            LinkedHashMap<String, List<String>> modeCopy = new LinkedHashMap<>();
            Objects.requireNonNull(modes, "modes for " + method)
                    .forEach((mode, selectors) -> {
                        if (mode == null || mode.isBlank()) {
                            throw new IllegalArgumentException(
                                    "configuration bucket must not be blank for " + method);
                        }
                        modeCopy.put(
                                mode,
                                List.copyOf(Objects.requireNonNull(
                                        selectors,
                                        "selectors for " + method + "/" + mode)));
                    });
            methods.put(method, Collections.unmodifiableMap(modeCopy));
        });
        return Collections.unmodifiableMap(methods);
    }
}
