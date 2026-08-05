package com.kltyton.autoseamblend.selection.method;

import com.kltyton.autoseamblend.inference.InferenceDecision;
import com.kltyton.autoseamblend.engine.plan.PlanIdentity;
import java.util.List;
import java.util.Objects;

/** 中文：具备完整槽位域以及可解释手动或推断决策的具体方法选择。 / English: Concrete method selection with a complete slot domain and an explainable manual/inferred decision. */
public record ConfiguredMethodPlan(
        PlanIdentity identity,
        ConnectionMethod requestedMethod,
        ConnectionMethod resolvedMethod,
        boolean manual,
        double confidence,
        List<String> reasons,
        MethodSlotDomain domain) {
    public ConfiguredMethodPlan {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        reasons = List.copyOf(reasons);
        Objects.requireNonNull(domain, "domain");
        if (resolvedMethod == ConnectionMethod.AUTO || domain.method() != resolvedMethod) {
            throw new IllegalArgumentException("resolved method and slot domain must be concrete and agree");
        }
    }

    public static ConfiguredMethodPlan manual(PlanIdentity identity, ConnectionMethod method) {
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto is not a manual method");
        }
        return new ConfiguredMethodPlan(
                identity,
                method,
                method,
                true,
                1.0,
                List.of("manual_method_override"),
                MethodSlotDomain.of(method));
    }

    public static ConfiguredMethodPlan fromDecision(
            PlanIdentity identity,
            InferenceDecision inference) {
        Objects.requireNonNull(inference, "inference");
        ConnectionMethod resolved = inference.requireResolvedMethod();
        return new ConfiguredMethodPlan(
                Objects.requireNonNull(identity, "identity"),
                inference.requestedMethod(),
                resolved,
                inference.manual(),
                switch (inference.confidence()) {
                    case CERTAIN -> 1.0;
                    case REJECTED -> throw new IllegalArgumentException("rejected inference has no method plan");
                },
                java.util.stream.Stream.concat(
                                inference.evidence().stream(),
                                inference.unknownFacts().stream().map(value -> "unknown:" + value))
                        .toList(),
                MethodSlotDomain.of(resolved));
    }

    /** 中文：在替换后的不可变计划标识下返回相同的具体方法语义。 / English: Returns the same concrete method semantics under a replacement immutable plan identity. */
    public ConfiguredMethodPlan withIdentity(PlanIdentity nextIdentity) {
        Objects.requireNonNull(nextIdentity, "nextIdentity");
        if (identity.equals(nextIdentity)) {
            return this;
        }
        return new ConfiguredMethodPlan(
                nextIdentity,
                requestedMethod,
                resolvedMethod,
                manual,
                confidence,
                reasons,
                domain);
    }
}
