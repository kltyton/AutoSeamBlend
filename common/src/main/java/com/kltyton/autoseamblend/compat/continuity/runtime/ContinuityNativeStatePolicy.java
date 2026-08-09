package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：把 Continuity 原生 replacement 方法映射为稳定的 provider kind；Loader 只负责构造
 * 其本地 QuadProcessor。
 *
 * English: Maps Continuity native replacement methods to stable provider kinds; loaders only
 * construct their local QuadProcessor implementation.
 */
public final class ContinuityNativeStatePolicy {
    private ContinuityNativeStatePolicy() {}

    public static Optional<ReplacementKind> replacementKind(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case CTM -> Optional.of(ReplacementKind.CTM);
            case CTM_COMPACT -> Optional.of(ReplacementKind.CTM_COMPACT);
            case HORIZONTAL -> Optional.of(ReplacementKind.HORIZONTAL);
            case VERTICAL -> Optional.of(ReplacementKind.VERTICAL);
            case HORIZONTAL_VERTICAL -> Optional.of(ReplacementKind.HORIZONTAL_VERTICAL);
            case VERTICAL_HORIZONTAL -> Optional.of(ReplacementKind.VERTICAL_HORIZONTAL);
            default -> Optional.empty();
        };
    }

    public enum ReplacementKind {
        CTM,
        CTM_COMPACT,
        HORIZONTAL,
        VERTICAL,
        HORIZONTAL_VERTICAL,
        VERTICAL_HORIZONTAL
    }
}
