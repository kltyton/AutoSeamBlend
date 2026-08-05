package com.kltyton.autoseamblend.selection.query;

import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import java.util.Objects;

/** 中文：胜出的原生扩展、Managed、配置或隐式 AutoBlend 策略。 / English: Winning native-extension, Managed, config, or implicit AutoBlend policy. */
public record QueryPolicyOwner(IntentProvenance provenance, AutoBlendPolicy policy) {
    public QueryPolicyOwner {
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(policy, "policy");
    }
}
