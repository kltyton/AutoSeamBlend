package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.compat.continuity.adapter.ContinuityObservedQuery;
import com.kltyton.autoseamblend.engine.ownership.AdapterAcceptedState;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import java.util.List;
import java.util.Objects;

/**
 * 中文：一个 Continuity root generation 中已接受状态的不可变 DTO。
 *
 * English: Immutable Continuity accepted state for one root generation.
 *
 * <p>The state contains only project-owned query/document values. Loader reload publication
 * objects stay at the adapters that construct and publish this value.</p>
 */
public record ContinuityAcceptedState(
        long generation,
        List<ContinuityAcceptedDocument> documents,
        int unclassifiedHolderCount)
        implements AdapterAcceptedState {
    public ContinuityAcceptedState {
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be non-negative");
        }
        documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
        if (unclassifiedHolderCount < 0) {
            throw new IllegalArgumentException("unclassifiedHolderCount must be non-negative");
        }
    }

    @Override
    public String engineId() {
        return "continuity";
    }

    @Override
    public ContinuityAcceptedState withGeneration(long nextGeneration) {
        return new ContinuityAcceptedState(nextGeneration, documents, unclassifiedHolderCount);
    }

    @Override
    public QueryObservation observe(
            ConnectionQuery query,
            EngineQueryContext nativeContext) {
        if (!(nativeContext instanceof ContinuityObservedQuery observed)
                || !query.equals(observed.query())) {
            return QueryObservation.empty();
        }
        return observed.observation();
    }
}
