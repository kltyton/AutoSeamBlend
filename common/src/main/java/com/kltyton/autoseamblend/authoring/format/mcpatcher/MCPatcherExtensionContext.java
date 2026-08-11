package com.kltyton.autoseamblend.authoring.format.mcpatcher;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 中文：把 MCPatcher 作者来源连接到原生 properties 构造器的 Loader 中立线程帧。
 * English: Loader-neutral thread frame joining MCPatcher author provenance to a native properties
 * constructor.
 */
public final class MCPatcherExtensionContext {
    private static final ThreadLocal<MCPatcherAuthorExtension> CURRENT = new ThreadLocal<>();

    private MCPatcherExtensionContext() {}

    public static Optional<MCPatcherAuthorExtension> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static <T> T call(
            MCPatcherAuthorExtension extension,
            Supplier<T> operation) {
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(operation, "operation");
        if (CURRENT.get() != null) {
            throw new IllegalStateException("nested MCPatcher author extension frame");
        }
        CURRENT.set(extension);
        try {
            return operation.get();
        } finally {
            CURRENT.remove();
        }
    }
}
