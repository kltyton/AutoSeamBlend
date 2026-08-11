package com.kltyton.autoseamblend.engine.ownership.fusion;

import com.kltyton.autoseamblend.engine.ownership.AdapterAcceptedState;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.engine.query.fusion.FusionObservedQuery;
import java.util.List;
import java.util.Objects;

/**
 * 中文：一个 Fusion root generation 中已接受状态的引擎中立 DTO。
 *
 * English: Engine-neutral accepted Fusion state for one root generation.
 */
public record FusionAcceptedState(
        long generation,
        List<FusionAcceptedTexture> textures,
        int opaqueNativeDocumentCount)
        implements AdapterAcceptedState {
    public FusionAcceptedState {
        if (generation < 0 || opaqueNativeDocumentCount < 0) {
            throw new IllegalArgumentException("generation and counts must be non-negative");
        }
        textures = List.copyOf(Objects.requireNonNull(textures, "textures"));
    }

    @Override
    public String engineId() {
        return "fusion";
    }

    @Override
    public FusionAcceptedState withGeneration(long nextGeneration) {
        return new FusionAcceptedState(nextGeneration, textures, opaqueNativeDocumentCount);
    }

    @Override
    public QueryObservation observe(
            ConnectionQuery query,
            EngineQueryContext nativeContext) {
        if (!(nativeContext instanceof FusionObservedQuery observed)
                || !query.equals(observed.query())) {
            return QueryObservation.empty();
        }
        return observed.observation();
    }
}
