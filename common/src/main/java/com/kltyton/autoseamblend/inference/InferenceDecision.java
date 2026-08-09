package com.kltyton.autoseamblend.inference;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 中文：一个确定性决策；被拒绝的自动决策绝不会虚构具体回退方法。 / English: One deterministic decision. Rejected automatic decisions never invent a concrete fallback. */
public record InferenceDecision(
        ConnectionMethod requestedMethod,
        Optional<ConnectionMethod> resolvedMethod,
        boolean manual,
        Confidence confidence,
        List<String> evidence,
        List<String> unknownFacts) {
    public InferenceDecision {
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        resolvedMethod = Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        Objects.requireNonNull(confidence, "confidence");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        unknownFacts = List.copyOf(Objects.requireNonNull(unknownFacts, "unknownFacts"));
        if (resolvedMethod.filter(method -> method == ConnectionMethod.AUTO).isPresent()) {
            throw new IllegalArgumentException("an inference decision must be concrete");
        }
        if (manual != (requestedMethod != ConnectionMethod.AUTO)) {
            throw new IllegalArgumentException("only explicit non-auto methods are manual decisions");
        }
        if (resolvedMethod.isPresent() != (confidence != Confidence.REJECTED)) {
            throw new IllegalArgumentException("only accepted decisions have a resolved method");
        }
        if (resolvedMethod.isEmpty() && unknownFacts.isEmpty()) {
            throw new IllegalArgumentException("a rejected decision must name unknown facts");
        }
    }

    public ConnectionMethod requireResolvedMethod() {
        return resolvedMethod.orElseThrow(() ->
                new IllegalStateException("automatic inference was rejected: " + unknownFacts));
    }

    public enum Confidence {
        CERTAIN,
        REJECTED
    }
}
