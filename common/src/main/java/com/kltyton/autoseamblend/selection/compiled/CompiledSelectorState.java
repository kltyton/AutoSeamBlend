package com.kltyton.autoseamblend.selection.compiled;

import java.util.List;
import java.util.Objects;

/**
 * 中文：Loader 无关的已编译选择器代次值对象；Loader 只额外持有自己的配置捕获或原生桥接。
 * English: Loader-neutral value object for one compiled selector generation; each Loader only
 * adds its own configuration capture or native bridge.
 */
public record CompiledSelectorState<T>(
        long generation,
        ConnectionRuleSet<T> rules,
        boolean automaticDiscovery,
        int selectorCount,
        String publicationReason,
        List<String> diagnostics) {
    public CompiledSelectorState {
        if (generation < 0 || selectorCount < 0) {
            throw new IllegalArgumentException(
                    "generation and selectorCount must be non-negative");
        }
        Objects.requireNonNull(rules, "rules");
        if (publicationReason == null || publicationReason.isBlank()) {
            throw new IllegalArgumentException(
                    "publicationReason must not be blank");
        }
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
