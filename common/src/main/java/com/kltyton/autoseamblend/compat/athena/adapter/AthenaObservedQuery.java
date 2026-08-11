package com.kltyton.autoseamblend.compat.athena.adapter;

import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import java.util.Objects;

/**
 * 中文：由 Athena 原生模型/provider 决策产生的精确查询观察 DTO。
 *
 * English: Immutable exact-query observation DTO produced by Athena's native
 * model/provider decision.
 */
public record AthenaObservedQuery(
        ConnectionQuery query,
        QueryObservation observation) implements EngineQueryContext {
    public AthenaObservedQuery {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(observation, "observation");
    }
}
