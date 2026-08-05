package com.kltyton.autoseamblend.engine.routing;

import com.kltyton.autoseamblend.engine.EngineIdentifiers;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：记录一次模型所有权捕获的保守失败边界，随根快照原子发布，避免缺失探针被误判为没有原生效果。
 *
 * English:
 * Records conservative failure boundaries for one model-ownership capture. The root snapshot
 * publishes them atomically so a missing probe is never mistaken for absence of a native effect.
 */
public final class NativeCaptureHealth {
    private NativeCaptureHealth() {}

    static Snapshot captureFailures(
            long generation,
            Set<String> unavailableProviders) {
        LinkedHashMap<String, EngineHealth> engines =
                new LinkedHashMap<>();
        for (String engineId : unavailableProviders) {
            engines.put(
                    EngineIdentifiers.require(engineId),
                    new EngineHealth(true, Set.of()));
        }
        return new Snapshot(generation, engines);
    }

    public record Snapshot(
            long generation,
            Map<String, EngineHealth> engines) {
        public Snapshot {
            if (generation < 0) {
                throw new IllegalArgumentException(
                        "capture health generation must be non-negative");
            }
            engines = Collections.unmodifiableMap(
                    new LinkedHashMap<>(
                            Objects.requireNonNull(
                                    engines,
                                    "engines")));
        }

        public static Snapshot empty() {
            return new Snapshot(0, Map.of());
        }

        public static Snapshot empty(
                long generation) {
            return new Snapshot(
                    generation,
                    Map.of());
        }

        public Optional<String> unknownDiagnostic(
                String engineId,
                BlockState state) {
            Objects.requireNonNull(state, "state");
            EngineHealth health = engines.get(
                    EngineIdentifiers.require(engineId));
            if (health == null) {
                return Optional.empty();
            }
            if (health.providerUnavailable()) {
                return Optional.of(
                        "MODEL_OWNERSHIP_CAPTURE_PROVIDER_UNAVAILABLE:"
                                + engineId);
            }
            return health.unavailableStates().contains(state)
                    ? Optional.of(
                            "MODEL_OWNERSHIP_CAPTURE_STATE_UNAVAILABLE:"
                                    + engineId)
                    : Optional.empty();
        }
    }

    public record EngineHealth(
            boolean providerUnavailable,
            Set<BlockState> unavailableStates) {
        public EngineHealth {
            unavailableStates = Collections.unmodifiableSet(
                    new LinkedHashSet<>(
                            Objects.requireNonNull(
                                    unavailableStates,
                                    "unavailableStates")));
        }
    }
}
