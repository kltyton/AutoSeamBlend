package com.kltyton.autoseamblend.compat.continuity.adapter;

import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import java.util.Objects;

/**
 * 中文：Continuity 原生谓词调用得到的、与引擎类型隔离的精确查询观察结果。
 * English: Engine-type-neutral exact query observation produced by Continuity's native predicate call.
 */
public record ContinuityObservedQuery(
        ConnectionQuery query,
        QueryObservation observation) implements EngineQueryContext {
    public ContinuityObservedQuery {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(observation, "observation");
    }
}
