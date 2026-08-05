package com.kltyton.autoseamblend.selection.method;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** 中文：配置方法的稳定槽位标识；方向和状态选择仍由引擎负责。 / English: Stable slot identities for a configured method; direction/state selection remains engine-owned. */
public record MethodSlotDomain(ConnectionMethod method, List<Integer> slots) {
    public MethodSlotDomain {
        Objects.requireNonNull(method, "method");
        slots = List.copyOf(slots);
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be inferred before constructing a slot domain");
        }
    }

    public static MethodSlotDomain of(ConnectionMethod method) {
        int count = switch (Objects.requireNonNull(method, "method")) {
            case AUTO -> throw new IllegalArgumentException("auto has no concrete slot domain");
            case NONE -> 0;
            case TOP, FIXED -> 1;
            case HORIZONTAL, VERTICAL -> 4;
            case CTM_COMPACT -> 5;
            case HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL -> 7;
            case RUNTIME_BLEND, OVERLAY -> 17;
            case CTM, OVERLAY_CTM -> 47;
        };
        return new MethodSlotDomain(method, IntStream.range(0, count).boxed().toList());
    }
}
