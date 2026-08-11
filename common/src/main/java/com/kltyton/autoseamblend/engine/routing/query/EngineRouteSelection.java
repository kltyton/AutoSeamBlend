package com.kltyton.autoseamblend.engine.routing.query;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.query.QueryResolution;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 中文：Loader 无关的查询路由结果。 / English: Loader-neutral query-routing result. */
public record EngineRouteSelection(
        EngineDescriptor engine,
        EngineRouteProvenance provenance,
        List<NativeSlot> nativeSlots,
        Optional<QueryResolution> resolution,
        ConnectionMethod method) {
    public EngineRouteSelection {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(provenance, "provenance");
        nativeSlots = List.copyOf(Objects.requireNonNull(nativeSlots, "nativeSlots"));
        resolution = Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(method, "method");
        resolution.ifPresent(value -> {
            if (value.selectedEngine().filter(engine::equals).isEmpty()
                    || value.method().resolvedMethod() != method) {
                throw new IllegalArgumentException("route and exact query resolution must agree");
            }
        });
    }

    public boolean runsAutoBlend() {
        return resolution
                .map(value -> switch (value.completion().outcome()) {
                    case FULL -> true;
                    case COMPLEMENT -> !value.completion().missingSlots().isEmpty();
                    case PASSTHROUGH, NATIVE_ONLY, CONSERVATIVE_NATIVE -> false;
                })
                .orElse(provenance.source() != EngineRouteSource.NATIVE_AUTHOR);
    }

    public boolean fillsSlot(int slot) {
        if (slot < 0) {
            return false;
        }
        return resolution
                .map(value -> value.completion().missingSlots().contains(slot))
                .orElse(provenance.source() != EngineRouteSource.NATIVE_AUTHOR);
    }

    public List<NativeSlot> protectedSlots() {
        return resolution
                .map(value -> value.completion().protectedSlots())
                .orElseGet(List::of);
    }
}
