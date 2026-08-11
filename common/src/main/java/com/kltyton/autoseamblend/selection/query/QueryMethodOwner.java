package com.kltyton.autoseamblend.selection.query;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;

/** 中文：拥有查询方法的最高五级来源，与 AutoBlend 策略无关。 / English: Highest five-tier source that owns the query method, independent of AutoBlend strategy. */
public record QueryMethodOwner(
        IntentProvenance provenance,
        Optional<ConnectionMethod> requestedMethod,
        Optional<ConnectionMethod> resolvedMethod) {
    public QueryMethodOwner {
        Objects.requireNonNull(provenance, "provenance");
        requestedMethod = Objects.requireNonNull(requestedMethod, "requestedMethod");
        resolvedMethod = Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        if (resolvedMethod.filter(method -> method == ConnectionMethod.AUTO).isPresent()) {
            throw new IllegalArgumentException("resolved method owner value must be concrete");
        }
    }
}
