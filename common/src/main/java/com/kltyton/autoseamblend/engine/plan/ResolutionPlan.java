package com.kltyton.autoseamblend.engine.plan;

import com.kltyton.autoseamblend.selection.query.QueryResolution;
import java.util.Objects;
import java.util.Optional;

/** 中文：其推断对象和方法对象由所有能力消费者共享的具体计划。 / English: One concrete plan whose inference and method objects are shared by every capability consumer. */
public record ResolutionPlan(
        QueryResolution queryResolution,
        MethodPlanResolution methodResolution,
        EngineExecutionPlan executionPlan,
        Optional<AuthorExecutionView> authorView) {
    public ResolutionPlan {
        Objects.requireNonNull(queryResolution, "queryResolution");
        Objects.requireNonNull(methodResolution, "methodResolution");
        Objects.requireNonNull(executionPlan, "executionPlan");
        authorView = Objects.requireNonNull(authorView, "authorView");
        if (!queryResolution.key().equals(executionPlan.resolutionKey())) {
            throw new IllegalArgumentException("resolution and execution keys must agree");
        }
        if (queryResolution.methodResolution() != methodResolution) {
            throw new IllegalArgumentException("query and plan must share one method resolution instance");
        }
        if (methodResolution.method() != executionPlan.method()) {
            throw new IllegalArgumentException("resolution and execution must share one method plan instance");
        }
        authorView.ifPresent(view -> {
            if (!view.contains(methodResolution)) {
                throw new IllegalArgumentException("author view must retain the exact query method resolution");
            }
        });
    }
}
