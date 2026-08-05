package com.kltyton.autoseamblend.reload;

import com.kltyton.autoseamblend.engine.ownership.AdapterAcceptedState;
import com.kltyton.autoseamblend.engine.ownership.NativeOwnershipSnapshot;
import com.kltyton.autoseamblend.engine.registry.EngineDiagnostic;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：资源重载 prepare 阶段跨 Loader 传递的不可变引擎贡献计划。
 *
 * English: Immutable engine contribution plan shared across Loader prepare phases.
 *
 * <p>The plan contains only project-owned data. Loader-specific tokens and native engine
 * objects stay at the adapter boundary.
 */
public record ReloadContributionPlan(
        long tokenOrdinal,
        long targetGeneration,
        String engineId,
        Optional<NativeOwnershipSnapshot> ownership,
        Optional<AdapterAcceptedState> acceptedState,
        List<EngineDiagnostic> diagnostics) {
    public ReloadContributionPlan {
        if (tokenOrdinal <= 0 || targetGeneration <= 0) {
            throw new IllegalArgumentException("tokenOrdinal and targetGeneration must be positive");
        }
        if (engineId == null || engineId.isBlank()) {
            throw new IllegalArgumentException("engineId must not be blank");
        }
        ownership = Objects.requireNonNull(ownership, "ownership");
        acceptedState = Objects.requireNonNull(acceptedState, "acceptedState");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (ownership.isPresent() != acceptedState.isPresent()) {
            throw new IllegalArgumentException(
                    "ownership and acceptedState must either both be present or both be absent");
        }
        acceptedState.ifPresent(state -> {
            if (!engineId.equals(state.engineId())) {
                throw new IllegalArgumentException("acceptedState belongs to another engine");
            }
        });
        if (ownership.isEmpty() && diagnostics.isEmpty()) {
            throw new IllegalArgumentException(
                    "an unavailable contribution must explain why it is unavailable");
        }
    }

    /**
     * 中文：构造包含完整原生所有权的就绪计划。 / English: Creates a ready plan with complete
     * native ownership state.
     */
    public static ReloadContributionPlan ready(
            long tokenOrdinal,
            long targetGeneration,
            String engineId,
            NativeOwnershipSnapshot ownership,
            AdapterAcceptedState acceptedState,
            List<EngineDiagnostic> diagnostics) {
        return new ReloadContributionPlan(
                tokenOrdinal,
                targetGeneration,
                engineId,
                Optional.of(Objects.requireNonNull(ownership, "ownership")),
                Optional.of(Objects.requireNonNull(acceptedState, "acceptedState")),
                diagnostics);
    }

    /**
     * 中文：构造带诊断的不可用计划。 / English: Creates an unavailable plan with diagnostics.
     */
    public static ReloadContributionPlan unavailable(
            long tokenOrdinal,
            long targetGeneration,
            String engineId,
            List<EngineDiagnostic> diagnostics) {
        return new ReloadContributionPlan(
                tokenOrdinal,
                targetGeneration,
                engineId,
                Optional.empty(),
                Optional.empty(),
                diagnostics);
    }

    /** 中文：是否包含可提交的完整状态。 / English: Whether complete state can be committed. */
    public boolean ready() {
        return ownership.isPresent();
    }

    /**
     * 中文：验证计划是否属于指定重载。 / English: Checks whether this plan belongs to a reload.
     */
    public boolean matches(long ordinal, long generation) {
        return tokenOrdinal == ordinal && targetGeneration == generation;
    }
}
