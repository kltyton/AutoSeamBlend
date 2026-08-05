package com.kltyton.autoseamblend.reload;

import com.kltyton.autoseamblend.engine.registry.EngineDiagnostic;
import java.util.List;
import java.util.Objects;

/**
 * 中文：冻结本次资源重载中各引擎的参与资格与诊断。
 *
 * English: Frozen engine participation eligibility and diagnostics for one resource reload.
 *
 * <p>Loader code supplies the discovery result; this value carries no Fabric, NeoForge, or
 * third-party engine type and can therefore be reused by any Loader orchestrator.
 */
public record ReloadEnginePlan(
        List<Engine> engines,
        List<EngineDiagnostic> diagnostics) {
    public ReloadEnginePlan {
        engines = List.copyOf(Objects.requireNonNull(engines, "engines"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public record Engine(
            String engineId,
            boolean nativeContributionEligible,
            boolean productSelectable) {
        public Engine {
            if (engineId == null || engineId.isBlank()) {
                throw new IllegalArgumentException("engineId must not be blank");
            }
        }
    }
}
