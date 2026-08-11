package com.kltyton.autoseamblend.engine.plan;

/** 中文：一个已解析方法计划的所有消费者共享的稳定标识。 / English: Stable identity shared by every consumer of one resolved method plan. */
public record PlanIdentity(
        long generation,
        String reloadToken,
        String engineId,
        String value) {
    public PlanIdentity {
        if (generation < 0) throw new IllegalArgumentException("generation must be non-negative");
        requireText(reloadToken, "reloadToken");
        requireText(engineId, "engineId");
        requireText(value, "value");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
