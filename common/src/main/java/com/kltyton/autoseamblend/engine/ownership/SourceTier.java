package com.kltyton.autoseamblend.engine.ownership;

/** 中文：精确的五级来源优先次序；更高值先于资源包优先级获胜。 / English: Exact five-level provenance precedence; higher values win before pack priority. */
public enum SourceTier {
    CONFIG_NON_COMPATIBILITY(100),
    CONFIG_COMPATIBILITY(200),
    MANAGED_NON_COMPATIBILITY(300),
    MANAGED_COMPATIBILITY(400),
    NATIVE_AUTHOR(500);

    private final int priority;

    SourceTier(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
