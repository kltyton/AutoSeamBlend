package com.kltyton.autoseamblend.engine.plan;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.engine.query.ExactSurfaceIdentity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 中文：在一个不变作者文档来源下保留的逐精确表面 auto 绑定。 / English: Per-exact-surface auto bindings retained under one unchanged author document provenance. */
public record AuthorExecutionView(
        AuthorRuleKey key,
        String reloadToken,
        Map<ExactSurfaceIdentity, MethodPlanResolution> exactBindings,
        Set<ExactSurfaceIdentity> unresolvedExactIdentities,
        RoutingState routingState) {
    public AuthorExecutionView {
        Objects.requireNonNull(key, "key");
        if (reloadToken == null || reloadToken.isBlank()) {
            throw new IllegalArgumentException("reloadToken must not be blank");
        }
        exactBindings = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(exactBindings, "exactBindings")));
        unresolvedExactIdentities = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(
                Objects.requireNonNull(unresolvedExactIdentities, "unresolvedExactIdentities")));
        Objects.requireNonNull(routingState, "routingState");
        if (exactBindings.isEmpty() && unresolvedExactIdentities.isEmpty()) {
            throw new IllegalArgumentException("author view requires at least one exact identity");
        }
        exactBindings.forEach((surface, resolution) -> {
            Objects.requireNonNull(surface, "exact surface identity");
            if (!reloadToken.equals(resolution.method().identity().reloadToken())
                    || !key.engineId().equals(resolution.method().identity().engineId())) {
                throw new IllegalArgumentException("author binding identity differs from its document");
            }
        });
        Map<ExactSurfaceIdentity, MethodPlanResolution> finalExactBindings = exactBindings;
        unresolvedExactIdentities.forEach(surface -> {
            Objects.requireNonNull(surface, "unresolved exact surface identity");
            if (finalExactBindings.containsKey(surface)) {
                throw new IllegalArgumentException("an exact surface cannot be both resolved and unresolved");
            }
        });
        long distinctMethods = exactBindings.values().stream()
                .map(resolution -> resolution.method().resolvedMethod())
                .distinct()
                .count();
        boolean nativeExecutionReady = unresolvedExactIdentities.isEmpty() && distinctMethods == 1;
        if ((routingState == RoutingState.NATIVE_EXECUTION_READY) != nativeExecutionReady) {
            throw new IllegalArgumentException("routing state must reflect exact binding method convergence");
        }
    }

    public Optional<MethodPlanResolution> nativeExecution() {
        return routingState == RoutingState.NATIVE_EXECUTION_READY
                ? exactBindings.values().stream().findFirst()
                : Optional.empty();
    }

    public boolean contains(MethodPlanResolution resolution) {
        return exactBindings.containsValue(Objects.requireNonNull(resolution, "resolution"));
    }

    public Map<ExactSurfaceIdentity, ConnectionMethod> resolvedMethods() {
        LinkedHashMap<ExactSurfaceIdentity, ConnectionMethod> methods = new LinkedHashMap<>();
        exactBindings.forEach((surface, resolution) ->
                methods.put(surface, resolution.method().resolvedMethod()));
        return Collections.unmodifiableMap(methods);
    }

    /** 中文：把每个已解析的精确表面方法重新绑定到一个快照代次。 / English: Rebinds every resolved exact-surface method to one snapshot generation. */
    public AuthorExecutionView withGeneration(long nextGeneration) {
        if (nextGeneration < 0) {
            throw new IllegalArgumentException("generation must be non-negative");
        }
        if (exactBindings.values().stream()
                .allMatch(resolution -> resolution.method().identity().generation() == nextGeneration)) {
            return this;
        }
        LinkedHashMap<ExactSurfaceIdentity, MethodPlanResolution> rebound = new LinkedHashMap<>();
        exactBindings.forEach((surface, resolution) -> {
            PlanIdentity identity = resolution.method().identity();
            rebound.put(surface, resolution.withIdentity(new PlanIdentity(
                    nextGeneration,
                    identity.reloadToken(),
                    identity.engineId(),
                    identity.value())));
        });
        return new AuthorExecutionView(
                key,
                reloadToken,
                rebound,
                unresolvedExactIdentities,
                routingState);
    }

    public enum RoutingState {
        NATIVE_EXECUTION_READY,
        ROUTING_PENDING
    }
}
