package com.kltyton.autoseamblend.selection.method;

import java.util.Locale;
import java.util.Optional;

/** 中文：按稳定配置遍历顺序排列的 AutoSeamBlend 公开方法。 / English: Public AutoSeamBlend methods in their stable configuration traversal order. */
public enum ConnectionMethod {
    AUTO,
    RUNTIME_BLEND,
    CTM,
    CTM_COMPACT,
    HORIZONTAL,
    VERTICAL,
    HORIZONTAL_VERTICAL,
    VERTICAL_HORIZONTAL,
    TOP,
    OVERLAY,
    OVERLAY_CTM,
    FIXED,
    NONE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * 中文：判断方法是否提供 overlay 供体语义。
     * English: Returns whether this method provides overlay-donor semantics.
     */
    public boolean overlayCapable() {
        return this == RUNTIME_BLEND
                || this == OVERLAY
                || this == OVERLAY_CTM;
    }

    public static Optional<ConnectionMethod> parse(String value) {
        if (value == null) return Optional.empty();
        for (ConnectionMethod method : values()) {
            if (method.serializedName().equals(value.trim())) return Optional.of(method);
        }
        return Optional.empty();
    }
}
