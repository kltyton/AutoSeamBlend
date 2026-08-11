package com.kltyton.autoseamblend.selection.compiled;

import java.util.List;

/**
 * 中文：由 Loader 捕获对象公开公共选择器代次的共享只读视图。
 *
 * English: Shared read-only view that exposes a common selector generation from a Loader capture.
 */
public interface CompiledSelectorView<T> {
    CompiledSelectorState<T> compiled();

    default long generation() {
        return compiled().generation();
    }

    default ConnectionRuleSet<T> rules() {
        return compiled().rules();
    }

    default boolean automaticDiscovery() {
        return compiled().automaticDiscovery();
    }

    default int selectorCount() {
        return compiled().selectorCount();
    }

    default String publicationReason() {
        return compiled().publicationReason();
    }

    default List<String> diagnostics() {
        return compiled().diagnostics();
    }
}
