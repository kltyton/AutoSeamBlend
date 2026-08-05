package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.EngineAdapter;
import java.util.List;
import java.util.Optional;

public record EngineSelection(
        EngineStatus.State state,
        Optional<EngineAdapter> adapter,
        String reason,
        List<EngineDiagnostic> diagnostics) {
    public EngineSelection {
        if (state == null) throw new NullPointerException("state");
        adapter = adapter == null ? Optional.empty() : adapter;
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean active() {
        return adapter.isPresent();
    }
}
