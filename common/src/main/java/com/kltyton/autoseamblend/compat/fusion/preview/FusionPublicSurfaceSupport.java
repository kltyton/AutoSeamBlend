package com.kltyton.autoseamblend.compat.fusion.preview;

import com.kltyton.autoseamblend.engine.capability.CapabilitySurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** 中文：Fusion 的源码能力矩阵。 / English: Fusion source-capability matrix. */
public final class FusionPublicSurfaceSupport {
    public static final String SOURCE_WIRING_STATUS =
            "Fusion 1.3.5 has source-wired runtime, Picture-in-Picture preview, PNG materialize, "
                    + "and collision-safe native baked export; manual runtime acceptance is NOT_RUN";

    private static final Map<ConnectionMethod, Map<CapabilitySurface, State>> CELLS = createCells();

    private FusionPublicSurfaceSupport() {}

    public static State state(ConnectionMethod method, CapabilitySurface surface) {
        return CELLS.get(Objects.requireNonNull(method, "method"))
                .get(Objects.requireNonNull(surface, "surface"));
    }

    public static Map<ConnectionMethod, Map<CapabilitySurface, State>> cells() {
        return CELLS;
    }

    private static Map<ConnectionMethod, Map<CapabilitySurface, State>> createCells() {
        EnumMap<ConnectionMethod, Map<CapabilitySurface, State>> cells =
                new EnumMap<>(ConnectionMethod.class);
        for (ConnectionMethod method : ConnectionMethod.values()) {
            EnumMap<CapabilitySurface, State> surfaces = new EnumMap<>(CapabilitySurface.class);
            surfaces.put(CapabilitySurface.RUNTIME, runtime(method));
            surfaces.put(CapabilitySurface.PREVIEW,
                    method == ConnectionMethod.NONE
                            ? State.IDENTITY_PASSTHROUGH
                            : State.SOURCE_WIRED_MANUAL_ACCEPTANCE_NOT_RUN);
            surfaces.put(CapabilitySurface.PNG_MATERIALIZE,
                    method == ConnectionMethod.NONE
                            ? State.IDENTITY_PASSTHROUGH
                            : State.SOURCE_WIRED_MANUAL_ACCEPTANCE_NOT_RUN);
            surfaces.put(CapabilitySurface.BAKED_EXPORT,
                    method == ConnectionMethod.NONE
                            ? State.IDENTITY_PASSTHROUGH
                            : State.SOURCE_WIRED_MANUAL_ACCEPTANCE_NOT_RUN);
            cells.put(method, Map.copyOf(surfaces));
        }
        return Map.copyOf(cells);
    }

    private static State runtime(ConnectionMethod method) {
        return switch (method) {
            case NONE -> State.IDENTITY_PASSTHROUGH;
            default -> State.SOURCE_WIRED_MANUAL_ACCEPTANCE_NOT_RUN;
        };
    }

    public static boolean isSourceComplete() {
        return CELLS.values().stream()
                .flatMap(surfaces -> surfaces.values().stream())
                .allMatch(State::sourceImplemented);
    }

    public enum State {
        IDENTITY_PASSTHROUGH(true),
        SOURCE_WIRED_MANUAL_ACCEPTANCE_NOT_RUN(true);

        private final boolean sourceImplemented;

        State(boolean sourceImplemented) {
            this.sourceImplemented = sourceImplemented;
        }

        public boolean sourceImplemented() {
            return sourceImplemented;
        }
    }
}
