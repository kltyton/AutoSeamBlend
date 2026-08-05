package com.kltyton.autoseamblend.engine.capability;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** 中文：不可变的 13 方法乘 4 能力面矩阵；缺失单元表示不支持。 / English: Immutable 13-method by 4-surface matrix. Missing cells are unsupported. */
public final class CapabilityMatrix {
    private final Map<ConnectionMethod, Map<CapabilitySurface, CapabilityState>> cells;

    private CapabilityMatrix(Map<ConnectionMethod, Map<CapabilitySurface, CapabilityState>> source) {
        EnumMap<ConnectionMethod, Map<CapabilitySurface, CapabilityState>> copy =
                new EnumMap<>(ConnectionMethod.class);
        for (ConnectionMethod method : ConnectionMethod.values()) {
            EnumMap<CapabilitySurface, CapabilityState> surfaces = new EnumMap<>(CapabilitySurface.class);
            for (CapabilitySurface surface : CapabilitySurface.values()) {
                surfaces.put(surface, source.getOrDefault(method, Map.of())
                        .getOrDefault(surface, CapabilityState.UNAVAILABLE));
            }
            copy.put(method, Map.copyOf(surfaces));
        }
        cells = Map.copyOf(copy);
    }

    public static CapabilityMatrix none() {
        return new CapabilityMatrix(Map.of());
    }

    public static CapabilityMatrix incomplete() {
        return uniform(CapabilityState.INCOMPLETE);
    }

    public static CapabilityMatrix complete() {
        return uniform(CapabilityState.IMPLEMENTED);
    }

    private static CapabilityMatrix uniform(CapabilityState state) {
        EnumMap<ConnectionMethod, Map<CapabilitySurface, CapabilityState>> cells =
                new EnumMap<>(ConnectionMethod.class);
        for (ConnectionMethod method : ConnectionMethod.values()) {
            EnumMap<CapabilitySurface, CapabilityState> surfaces = new EnumMap<>(CapabilitySurface.class);
            for (CapabilitySurface surface : CapabilitySurface.values()) surfaces.put(surface, state);
            cells.put(method, surfaces);
        }
        return new CapabilityMatrix(cells);
    }

    public static CapabilityMatrix of(
            Map<ConnectionMethod, Map<CapabilitySurface, CapabilityState>> cells) {
        return new CapabilityMatrix(Objects.requireNonNull(cells, "cells"));
    }

    public boolean supports(ConnectionMethod method, CapabilitySurface surface) {
        return state(method, surface) == CapabilityState.IMPLEMENTED;
    }

    public CapabilityState state(ConnectionMethod method, CapabilitySurface surface) {
        return cells.get(Objects.requireNonNull(method, "method"))
                .get(Objects.requireNonNull(surface, "surface"));
    }

    /** 中文：13 乘 4 的每个单元均已实现。 / English: Every one of the 13 x 4 cells is implemented. */
    public boolean isComplete() {
        return cells.values().stream().flatMap(value -> value.values().stream())
                .allMatch(state -> state == CapabilityState.IMPLEMENTED);
    }

    public boolean isImplementationComplete() {
        return isComplete();
    }

    public Map<ConnectionMethod, Map<CapabilitySurface, CapabilityState>> asMap() {
        return cells;
    }
}
