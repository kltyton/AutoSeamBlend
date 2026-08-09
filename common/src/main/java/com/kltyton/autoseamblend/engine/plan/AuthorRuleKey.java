package com.kltyton.autoseamblend.engine.plan;

/** 中文：一次重载期间一个作者文档的引擎无关标识。 / English: Engine-neutral identity of one author document during one reload. */
public record AuthorRuleKey(
        String engineId,
        String packId,
        String resourceId,
        int packPriority) {
    public AuthorRuleKey {
        requireText(engineId, "engineId");
        requireText(packId, "packId");
        requireText(resourceId, "resourceId");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
