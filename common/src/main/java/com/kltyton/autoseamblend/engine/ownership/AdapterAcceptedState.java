package com.kltyton.autoseamblend.engine.ownership;

import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;

/** 中文：一个适配器不可变已接受规则状态的引擎无关句柄。 / English: Engine-neutral handle for one adapter's immutable accepted-rule state. */
public interface AdapterAcceptedState {
    String engineId();

    long generation();

    AdapterAcceptedState withGeneration(long generation);

    /** 中文：通过此适配器原生谓词生命周期观察到的精确已接受所有权。 / English: Exact accepted ownership observed through this adapter's native predicate lifecycle. */
    default QueryObservation observe(
            ConnectionQuery query,
            EngineQueryContext nativeContext) {
        return QueryObservation.empty();
    }
}
