package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.EngineAdapter;
import com.kltyton.autoseamblend.engine.capability.CapabilityMatrix;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：一个候选适配器的中立能力证明；失败原因可由 Loader 桥提供但不携带第三方对象。
 * English: Neutral capability proof for one candidate; a loader bridge may provide a failure
 * reason without exposing third-party objects.
 */
public record EngineAdapterProvision(
        CapabilityMatrix capabilities,
        Optional<EngineAdapter> adapter,
        Optional<EngineDiagnostic> failure) {
    public EngineAdapterProvision {
        Objects.requireNonNull(capabilities, "capabilities");
        adapter = Objects.requireNonNull(adapter, "adapter");
        failure = Objects.requireNonNull(failure, "failure");
        if (adapter.isPresent()
                && !adapter.orElseThrow().capabilities().asMap().equals(capabilities.asMap())) {
            throw new IllegalArgumentException("provision capabilities must match adapter capabilities");
        }
        if (adapter.isPresent() && failure.isPresent()) {
            throw new IllegalArgumentException("available provision cannot carry a failure");
        }
    }

    public static EngineAdapterProvision available(EngineAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        return new EngineAdapterProvision(
                adapter.capabilities(),
                Optional.of(adapter),
                Optional.empty());
    }

    public static EngineAdapterProvision unavailable(
            CapabilityMatrix capabilities,
            EngineDiagnostic failure) {
        return new EngineAdapterProvision(
                Objects.requireNonNull(capabilities, "capabilities"),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(failure, "failure")));
    }

}
