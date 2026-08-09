package com.kltyton.autoseamblend.engine.routing;

import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：由重载发布的原生模型所有权，在任何适配器包装模型前捕获。 / English: Reload-published native model ownership, captured before any adapter wraps models. */
public final class ModelOwnershipRuntime {
    private static final ConcurrentHashMap<
                    String,
                    NativeModelOwnershipProvider>
            PROVIDERS = new ConcurrentHashMap<>();

    private ModelOwnershipRuntime() {}

    public static void register(
            NativeModelOwnershipProvider provider) {
        Objects.requireNonNull(provider, "provider");
        NativeModelOwnershipProvider previous =
                PROVIDERS.putIfAbsent(
                        provider.engineId(),
                        provider);
        if (previous != null
                && previous != provider
                && !previous.getClass()
                        .equals(provider.getClass())) {
            throw new IllegalStateException(
                    "native model ownership provider already registered: "
                            + provider.engineId());
        }
    }

    /**
     * 中文：完整捕获每个提供者；任一状态失败会丢弃该提供者的整个候选，并把新代次标记为 UNKNOWN。
     *
     * English:
     * Captures every provider completely. A failure for any state discards that provider's whole
     * candidate and marks the new generation UNKNOWN.
     */
    public static synchronized PreparedCapture prepare(
            Map<BlockState, BakedModel> models,
            long generation) {
        Objects.requireNonNull(models, "models");
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "generation must be positive");
        }
        ArrayList<NativeModelOwnershipProvider> providers =
                new ArrayList<>(PROVIDERS.values());
        providers.sort(Comparator.comparingInt(
                provider -> provider.family()
                        .stableOrder()));
        LinkedHashMap<BlockState, Set<EngineFamily>> owners =
                new LinkedHashMap<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        ArrayList<NativeModelOwnershipProvider> capturing =
                new ArrayList<>();
        LinkedHashSet<String> unavailableProviders =
                new LinkedHashSet<>();
        boolean complete = false;
        try {
            for (NativeModelOwnershipProvider provider : providers) {
                try {
                    provider.beginCapture(generation);
                    capturing.add(provider);
                } catch (RuntimeException exception) {
                    rejectProvider(
                            provider,
                            generation,
                            "MODEL_OWNERSHIP_CAPTURE_BEGIN_REJECTED",
                            exception,
                            unavailableProviders,
                            diagnostics);
                }
            }
            for (Map.Entry<BlockState, BakedModel> entry
                    : models.entrySet()) {
                BlockState state = entry.getKey();
                BakedModel model = entry.getValue();
                for (NativeModelOwnershipProvider provider
                        : List.copyOf(capturing)) {
                    try {
                        if (provider.owns(model)) {
                            provider.capture(state, model);
                            owners.computeIfAbsent(
                                            state,
                                            ignored -> EnumSet.noneOf(
                                                    EngineFamily.class))
                                    .add(provider.family());
                        }
                    } catch (RuntimeException exception) {
                        capturing.remove(provider);
                        removeFamily(
                                owners,
                                provider.family());
                        rejectProvider(
                                provider,
                                generation,
                                "MODEL_OWNERSHIP_CAPTURE_PROVIDER_REJECTED",
                                exception,
                                unavailableProviders,
                                diagnostics);
                    }
                }
            }
            ArrayList<NativeModelOwnershipProvider> staged =
                    new ArrayList<>();
            for (NativeModelOwnershipProvider provider
                    : List.copyOf(capturing)) {
                try {
                    provider.endCapture();
                    staged.add(provider);
                } catch (RuntimeException exception) {
                    removeFamily(
                            owners,
                            provider.family());
                    rejectProvider(
                            provider,
                            generation,
                            "MODEL_OWNERSHIP_CAPTURE_END_REJECTED",
                            exception,
                            unavailableProviders,
                            diagnostics);
                }
            }
            owners.replaceAll((state, families) ->
                    Set.copyOf(families));
            Snapshot snapshot = new Snapshot(
                    generation,
                    owners,
                    diagnostics);
            PreparedCapture prepared = new PreparedCapture(
                    snapshot,
                    NativeCaptureHealth.captureFailures(
                            generation,
                            unavailableProviders),
                    staged);
            complete = true;
            return prepared;
        } finally {
            if (!complete) {
                for (NativeModelOwnershipProvider provider
                        : capturing) {
                    abortProvider(
                            provider,
                            generation,
                            diagnostics);
                }
            }
        }
    }

    public static synchronized void abort(
            PreparedCapture prepared) {
        Objects.requireNonNull(prepared, "prepared");
        ArrayList<String> ignoredDiagnostics =
                new ArrayList<>();
        for (NativeModelOwnershipProvider provider
                : prepared.providers()) {
            abortProvider(
                    provider,
                    prepared.snapshot()
                            .generation(),
                    ignoredDiagnostics);
        }
    }

    private static void rejectProvider(
            NativeModelOwnershipProvider provider,
            long generation,
            String diagnostic,
            RuntimeException exception,
            Set<String> unavailableProviders,
            List<String> diagnostics) {
        unavailableProviders.add(provider.engineId());
        diagnostics.add(diagnostic
                + ':'
                + provider.engineId()
                + ':'
                + exception.getClass()
                        .getSimpleName());
        abortProvider(
                provider,
                generation,
                diagnostics);
    }

    private static void removeFamily(
            Map<BlockState, Set<EngineFamily>> owners,
            EngineFamily family) {
        owners.values().forEach(values ->
                values.remove(family));
        owners.entrySet().removeIf(entry ->
                entry.getValue().isEmpty());
    }

    private static void abortProvider(
            NativeModelOwnershipProvider provider,
            long generation,
            List<String> diagnostics) {
        try {
            provider.abortCapture(generation);
        } catch (RuntimeException abortFailure) {
            diagnostics.add(
                    "MODEL_OWNERSHIP_CAPTURE_ABORT_REJECTED:"
                            + provider.engineId()
                            + ':'
                            + abortFailure.getClass()
                                    .getSimpleName());
        }
    }

    public record PreparedCapture(
            Snapshot snapshot,
            NativeCaptureHealth.Snapshot health,
            List<NativeModelOwnershipProvider> providers) {
        public PreparedCapture {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(health, "health");
            providers = List.copyOf(
                    Objects.requireNonNull(
                            providers,
                            "providers"));
            if (snapshot.generation()
                    != health.generation()) {
                throw new IllegalArgumentException(
                        "ownership and capture health must share one generation");
            }
        }
    }

    public record Snapshot(
            long generation,
            Map<BlockState, Set<EngineFamily>> owners,
            List<String> diagnostics) {
        public Snapshot {
            if (generation < 0) {
                throw new IllegalArgumentException(
                        "ownership generation must be non-negative");
            }
            LinkedHashMap<BlockState, Set<EngineFamily>> copy =
                    new LinkedHashMap<>();
            Objects.requireNonNull(owners, "owners")
                    .forEach((state, values) ->
                            copy.put(
                                    state,
                                    Set.copyOf(values)));
            owners = Collections.unmodifiableMap(copy);
            diagnostics = List.copyOf(
                    Objects.requireNonNull(
                            diagnostics,
                            "diagnostics"));
        }

        public static Snapshot empty() {
            return empty(0);
        }

        public static Snapshot empty(
                long generation) {
            return new Snapshot(
                    generation,
                    Map.of(),
                    List.of());
        }

        public Set<EngineFamily> owners(
                BlockState state) {
            return owners.getOrDefault(
                    state,
                    Set.of());
        }
    }
}
