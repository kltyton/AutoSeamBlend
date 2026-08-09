package com.kltyton.autoseamblend.engine.plan;

import com.kltyton.autoseamblend.inference.InferenceDecision;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.selection.method.ConfiguredMethodPlan;
import java.util.Objects;

/** 中文：一次推断求值产生的事实、决策和唯一具体方法对象。 / English: Facts, decision, and the single concrete method object produced by one inference evaluation. */
public record MethodPlanResolution(
        InferenceFacts facts,
        InferenceDecision decision,
        ConfiguredMethodPlan method) {
    public MethodPlanResolution {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(method, "method");
        if (decision.requestedMethod() != method.requestedMethod()
                || decision.requireResolvedMethod() != method.resolvedMethod()) {
            throw new IllegalArgumentException("facts decision and concrete method must describe one resolution");
        }
    }

    /** 中文：保留已求值事实与决策，仅重新绑定方法计划标识。 / English: Rebinds only the method-plan identity while retaining the evaluated facts and decision. */
    public MethodPlanResolution withIdentity(PlanIdentity nextIdentity) {
        Objects.requireNonNull(nextIdentity, "nextIdentity");
        if (method.identity().equals(nextIdentity)) {
            return this;
        }
        return new MethodPlanResolution(facts, decision, method.withIdentity(nextIdentity));
    }
}
