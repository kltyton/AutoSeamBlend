package com.kltyton.autoseamblend.engine.registry;

import java.util.List;

/** 中文：发现与验证状态；只有 READY 注册项参与选择。 / English: Discover/validate state. Only READY registrations participate in Select. */
public record EngineStatus(State state, List<EngineDiagnostic> diagnostics) {
    public EngineStatus {
        if (state == null) throw new NullPointerException("state");
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean selectable() {
        return state == State.READY || state == State.SELECTED;
    }

    public enum State {
        NOT_INSTALLED,
        DISCOVERED,
        INVALID_VERSION,
        INVALID_HOOKS,
        INCOMPLETE,
        READY,
        SELECTED,
        ENGINE_REQUIRED
    }
}
