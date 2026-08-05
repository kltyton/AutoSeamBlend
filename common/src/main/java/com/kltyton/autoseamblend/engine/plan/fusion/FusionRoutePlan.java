package com.kltyton.autoseamblend.engine.plan.fusion;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionSheetMethodPlan;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：一次冻结解析产生的 Fusion 公共路由值对象；不携带 Fusion 或 Loader 类型。
 *
 * English: Loader-neutral value object for one frozen Fusion route resolution; it carries no
 * Fusion or Loader types.
 */
public record FusionRoutePlan(
        ConnectionMethod requestedMethod,
        ConnectionMethod resolvedMethod,
        Kind kind,
        FusionSheetMethodPlan.Layout layout,
        int logicalSlotCount) {
    private static final Map<ConnectionMethod, Definition> DEFINITIONS = definitions();

    public FusionRoutePlan {
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        Objects.requireNonNull(kind, "kind");
        if (resolvedMethod == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("resolvedMethod must be concrete");
        }
        if (requestedMethod != ConnectionMethod.AUTO && requestedMethod != resolvedMethod) {
            throw new IllegalArgumentException("manual method must equal resolved method");
        }
        boolean usesConnectingSheet = kind == Kind.REPLACEMENT
                || kind == Kind.OVERLAY_DONOR
                || kind == Kind.TOP_SURFACE;
        if (usesConnectingSheet != (layout != null)) {
            throw new IllegalArgumentException("sheet-backed routes require a Fusion layout");
        }
        if (logicalSlotCount < 0) {
            throw new IllegalArgumentException("logicalSlotCount must be non-negative");
        }
    }

    public static FusionRoutePlan resolve(
            ConnectionMethod requestedMethod,
            ConnectionMethod resolvedMethod) {
        Definition definition = DEFINITIONS.get(Objects.requireNonNull(resolvedMethod, "resolvedMethod"));
        if (definition == null) {
            throw new IllegalArgumentException("AUTO must be resolved before Fusion routing");
        }
        return new FusionRoutePlan(
                Objects.requireNonNull(requestedMethod, "requestedMethod"),
                resolvedMethod,
                definition.kind(),
                definition.layout(),
                definition.logicalSlotCount());
    }

    private static Map<ConnectionMethod, Definition> definitions() {
        EnumMap<ConnectionMethod, Definition> definitions = new EnumMap<>(ConnectionMethod.class);
        for (ConnectionMethod method : ConnectionMethod.values()) {
            if (method == ConnectionMethod.AUTO) {
                continue;
            }
            Kind kind = switch (method) {
                case RUNTIME_BLEND, OVERLAY, OVERLAY_CTM -> Kind.OVERLAY_DONOR;
                case CTM, CTM_COMPACT, HORIZONTAL, VERTICAL,
                        HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL -> Kind.REPLACEMENT;
                case TOP -> Kind.TOP_SURFACE;
                case FIXED -> Kind.FIXED_PASSTHROUGH;
                case NONE -> Kind.PASSTHROUGH;
                case AUTO -> throw new AssertionError("AUTO is filtered above");
            };
            FusionSheetMethodPlan.Layout layout = switch (kind) {
                case REPLACEMENT, OVERLAY_DONOR, TOP_SURFACE ->
                        FusionSheetMethodPlan.layout(method);
                case FIXED_PASSTHROUGH, PASSTHROUGH -> null;
            };
            definitions.put(method, new Definition(
                    kind,
                    layout,
                    FusionSheetMethodPlan.logicalSlots(method).size()));
        }
        if (definitions.size() != ConnectionMethod.values().length - 1) {
            throw new IllegalStateException("Fusion route definitions must cover every concrete method");
        }
        return Map.copyOf(definitions);
    }

    public enum Kind {
        REPLACEMENT,
        OVERLAY_DONOR,
        TOP_SURFACE,
        FIXED_PASSTHROUGH,
        PASSTHROUGH
    }

    private record Definition(
            Kind kind,
            FusionSheetMethodPlan.Layout layout,
            int logicalSlotCount) {}
}
