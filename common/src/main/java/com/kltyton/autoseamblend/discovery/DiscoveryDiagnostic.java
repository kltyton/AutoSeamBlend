package com.kltyton.autoseamblend.discovery;

import java.util.Objects;

/** 中文：组装发现代次时产生的稳定解释。 / English: Stable explanation emitted while a discovery generation is assembled. */
public record DiscoveryDiagnostic(Severity severity, String code, String targetId, String detail) {
    public DiscoveryDiagnostic {
        severity = Objects.requireNonNull(severity, "severity");
        code = requireText(code, "code");
        targetId = targetId == null ? "" : targetId;
        detail = requireText(detail, "detail");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
