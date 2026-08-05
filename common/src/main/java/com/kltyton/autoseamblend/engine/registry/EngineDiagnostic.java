package com.kltyton.autoseamblend.engine.registry;

import java.util.Map;

public record EngineDiagnostic(Severity severity, String code, String message, Map<String, String> details) {
    public EngineDiagnostic {
        if (severity == null) throw new NullPointerException("severity");
        requireText(code, "code");
        requireText(message, "message");
        details = Map.copyOf(details);
    }

    public static EngineDiagnostic error(String code, String message) {
        return new EngineDiagnostic(Severity.ERROR, code, message, Map.of());
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
