package com.kltyton.autoseamblend.export.model;

import java.util.Objects;

public record ExportDiagnostic(Level level, String code, String message, String groupId)
        implements Comparable<ExportDiagnostic> {
    public ExportDiagnostic {
        Objects.requireNonNull(level, "level");
        code = requireText(code, "code");
        message = requireText(message, "message");
        groupId = groupId == null ? "" : groupId;
    }

    @Override
    public int compareTo(ExportDiagnostic other) {
        int order = level.compareTo(other.level);
        if (order == 0) order = groupId.compareTo(other.groupId);
        if (order == 0) order = code.compareTo(other.code);
        return order == 0 ? message.compareTo(other.message) : order;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    public enum Level { INFO, WARNING, ERROR }
}
