package com.kltyton.autoseamblend.fabric.reload.contribution;

import com.kltyton.autoseamblend.engine.ownership.AdapterAcceptedState;
import com.kltyton.autoseamblend.engine.ownership.NativeOwnershipSnapshot;
import java.util.List;
import java.util.Objects;

/**
 * 中文：引擎兼容边界在 prepare 阶段提交的不可变项目 DTO；不含任何第三方引擎对象。
 * English: Immutable project DTO staged by engine compat boundaries during
 * prepare; it carries no third-party engine object.
 */
public record FabricPreparedContribution(
        long tokenOrdinal,
        long targetGeneration,
        String engineId,
        NativeOwnershipSnapshot ownership,
        AdapterAcceptedState acceptedState,
        List<String> diagnostics) {
    public FabricPreparedContribution {
        if (tokenOrdinal < 0 || targetGeneration <= 0) {
            throw new IllegalArgumentException(
                    "contribution token and generation must be non-negative");
        }
        Objects.requireNonNull(engineId, "engineId");
        Objects.requireNonNull(ownership, "ownership");
        Objects.requireNonNull(acceptedState, "acceptedState");
        diagnostics = List.copyOf(
                Objects.requireNonNull(
                        diagnostics,
                        "diagnostics"));
    }

    public static FabricPreparedContribution ready(
            long tokenOrdinal,
            long targetGeneration,
            String engineId,
            NativeOwnershipSnapshot ownership,
            AdapterAcceptedState acceptedState,
            List<String> diagnostics) {
        return new FabricPreparedContribution(
                tokenOrdinal,
                targetGeneration,
                engineId,
                ownership,
                acceptedState,
                diagnostics);
    }
}
