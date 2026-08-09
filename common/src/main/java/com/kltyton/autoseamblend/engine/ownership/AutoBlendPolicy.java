package com.kltyton.autoseamblend.engine.ownership;

/** 中文：由原生扩展、Managed 文档、配置桶或隐式发现选择的查询局部策略。 / English: Query-local policy selected by a native extension, Managed document, config bucket, or implicit discovery. */
public enum AutoBlendPolicy {
    ALLOW_COMPLETION,
    NATIVE_EXCLUSIVE;

    public static AutoBlendPolicy fromCompatibility(boolean compatibility) {
        return compatibility ? ALLOW_COMPLETION : NATIVE_EXCLUSIVE;
    }

    public boolean allowsCompletion() {
        return this == ALLOW_COMPLETION;
    }
}
