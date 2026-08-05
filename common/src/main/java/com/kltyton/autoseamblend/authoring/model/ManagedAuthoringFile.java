package com.kltyton.autoseamblend.authoring.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 中文：为一次显式 Managed 保存准备的不可变原生格式文档。 / English: One immutable native-format document prepared for an explicit Managed save. */
public record ManagedAuthoringFile(String relativePath, byte[] content) {
    public ManagedAuthoringFile {
        relativePath = validatePath(relativePath);
        content = Objects.requireNonNull(content, "content").clone();
    }

    public static ManagedAuthoringFile utf8(
            String relativePath,
            String source) {
        return new ManagedAuthoringFile(
                relativePath,
                Objects.requireNonNull(source, "source")
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    private static String validatePath(String raw) {
        Objects.requireNonNull(raw, "relativePath");
        if (raw.isBlank()
                || raw.indexOf('\\') >= 0
                || raw.startsWith("/")
                || raw.endsWith("/")
                || raw.contains("//")
                || raw.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(
                    "invalid Managed relative path: " + raw);
        }
        for (String segment : raw.split("/", -1)) {
            if (segment.isEmpty()
                    || segment.equals(".")
                    || segment.equals("..")
                    || segment.indexOf(':') >= 0) {
                throw new IllegalArgumentException(
                        "invalid Managed relative path: " + raw);
            }
        }
        return raw;
    }
}
