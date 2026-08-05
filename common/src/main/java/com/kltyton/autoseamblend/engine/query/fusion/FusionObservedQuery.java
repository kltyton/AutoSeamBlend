package com.kltyton.autoseamblend.engine.query.fusion;

import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import java.util.Objects;

/**
 * 中文：Fusion 原生生命周期完成精确查询后交给通用层的不可变观察结果。
 *
 * English: Immutable observation handed to the common layer after Fusion's native query
 * lifecycle has produced its exact result.
 */
public record FusionObservedQuery(
        ConnectionQuery query,
        QueryObservation observation) implements EngineQueryContext {
    public FusionObservedQuery {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(observation, "observation");
    }
}
