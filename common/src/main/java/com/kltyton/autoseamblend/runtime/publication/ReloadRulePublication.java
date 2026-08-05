package com.kltyton.autoseamblend.runtime.publication;

import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;

/**
 * 中文：把共享选择器运行时接到根资源代次发布。
 * English: Connects the shared selector runtime to root resource-generation publication.
 */
public enum ReloadRulePublication implements RuleRuntime.Publication {
    INSTANCE;

    @Override
    public RuleRuntime.Snapshot current() {
        return ReloadPublication.current().selectors();
    }

    @Override
    public long nextGeneration() {
        return ReloadPublication.nextGeneration();
    }

    @Override
    public RuleRuntime.Snapshot publish(RuleRuntime.Snapshot candidate) {
        return ReloadPublication.publishSelectors(candidate);
    }
}
