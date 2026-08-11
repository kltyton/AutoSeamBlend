package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.kltyton.autoseamblend.engine.plan.fusion.FusionRoutePlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.supermartijn642.fusion.api.texture.types.connecting.ConnectingTextureData;
import java.util.Objects;

/** 中文：一次冻结解析产生的 Fusion 公共 API 路由。 / English: Frozen Fusion public-API route. */
public record FusionNativeRoute(
        ConnectionMethod requestedMethod,
        ConnectionMethod resolvedMethod,
        Kind kind,
        ConnectingTextureData.Layout layout,
        int logicalSlotCount) {
    public FusionNativeRoute {
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

    public static FusionNativeRoute resolve(
            ConnectionMethod requestedMethod,
            ConnectionMethod resolvedMethod) {
        FusionRoutePlan plan = FusionRoutePlan.resolve(
                Objects.requireNonNull(requestedMethod, "requestedMethod"),
                Objects.requireNonNull(resolvedMethod, "resolvedMethod"));
        return new FusionNativeRoute(
                plan.requestedMethod(),
                plan.resolvedMethod(),
                Kind.valueOf(plan.kind().name()),
                plan.layout() == null
                        ? null
                        : ConnectingTextureData.Layout.valueOf(plan.layout().name()),
                plan.logicalSlotCount());
    }

    public enum Kind {
        REPLACEMENT,
        OVERLAY_DONOR,
        TOP_SURFACE,
        FIXED_PASSTHROUGH,
        PASSTHROUGH
    }
}
