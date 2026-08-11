package com.kltyton.autoseamblend.authoring.format.mcpatcher;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * 中文：两个 MCPatcher 创作扩展及其内存执行副本的 Loader 中立严格解析器。
 *
 * English: Loader-neutral strict parser for the two MCPatcher authoring
 * extensions and their in-memory execution copy.
 */
public final class MCPatcherExecutionProperties {
    private MCPatcherExecutionProperties() {}

    public static Result prepare(Properties author) {
        Objects.requireNonNull(author, "author");
        String requestedLiteral = author.getProperty("method", "ctm").trim();
        Optional<ConnectionMethod> parsed =
                MCPatcherMethodCodec.parsePublic(requestedLiteral);
        if (parsed.isEmpty()) {
            return Result.rejected(
                    "PUBLIC_METHOD_UNSUPPORTED:" + requestedLiteral);
        }
        Compatibility compatibility = parseCompatibility(author);
        if (compatibility.rejection().isPresent()) {
            return Result.rejected(
                    compatibility.rejection().orElseThrow());
        }
        ConnectionMethod requested = parsed.orElseThrow();
        Optional<ConnectionMethod> resolved =
                requested == ConnectionMethod.AUTO
                        ? MCPatcherMethodCodec.resolveExplicitAutoConstraint(author)
                        : Optional.of(requested);
        /*
         * 中文：不受约束或有歧义的 auto 文档只创建原生 fixed/skip 谓词载体；具体方法由同一冻结 generation 的精确表面规划解析。
         *
         * English: An unconstrained or ambiguous auto document creates only a
         * native fixed/skip predicate carrier. Exact-surface planning resolves
         * the concrete method from the same frozen generation.
         */
        ConnectionMethod concrete = resolved.orElse(ConnectionMethod.NONE);
        Properties runtime = new Properties();
        runtime.putAll(author);
        runtime.remove("compatibility");
        runtime.setProperty(
                "method",
                MCPatcherMethodCodec.nativeMethod(concrete));
        if (concrete == ConnectionMethod.NONE) {
            runtime.setProperty("tiles", "<skip>");
        }
        return Result.accepted(
                new Prepared(
                        runtime,
                        requested,
                        concrete,
                        requested == ConnectionMethod.AUTO
                                && resolved.isEmpty(),
                        compatibility.value()));
    }

    private static Compatibility parseCompatibility(Properties properties) {
        if (!properties.containsKey("compatibility")) {
            return Compatibility.accepted(Optional.of(true));
        }
        String raw = properties.getProperty("compatibility");
        if (raw == null) {
            return Compatibility.rejected("COMPATIBILITY_VALUE_MISSING");
        }
        return switch (raw.trim()) {
            case "", "true" -> Compatibility.accepted(Optional.of(true));
            case "false" -> Compatibility.accepted(Optional.of(false));
            default -> Compatibility.rejected(
                    "COMPATIBILITY_MUST_BE_TRUE_OR_FALSE");
        };
    }

    public record Prepared(
            Properties runtimeProperties,
            ConnectionMethod requestedMethod,
            ConnectionMethod resolvedMethod,
            boolean exactSurfaceResolutionRequired,
            Optional<Boolean> compatibility) {
        public Prepared {
            runtimeProperties = Objects.requireNonNull(
                    runtimeProperties,
                    "runtimeProperties");
            requestedMethod = Objects.requireNonNull(
                    requestedMethod,
                    "requestedMethod");
            resolvedMethod = Objects.requireNonNull(
                    resolvedMethod,
                    "resolvedMethod");
            compatibility = Objects.requireNonNull(
                    compatibility,
                    "compatibility");
            if (resolvedMethod == ConnectionMethod.AUTO) {
                throw new IllegalArgumentException(
                        "execution method must be concrete");
            }
            if (exactSurfaceResolutionRequired
                    && (requestedMethod != ConnectionMethod.AUTO
                            || resolvedMethod != ConnectionMethod.NONE)) {
                throw new IllegalArgumentException(
                        "only unconstrained auto requires exact-surface resolution");
            }
        }
    }

    public record Result(
            Optional<Prepared> prepared,
            Optional<String> rejection) {
        public Result {
            prepared = Objects.requireNonNull(prepared, "prepared");
            rejection = Objects.requireNonNull(rejection, "rejection");
            if (prepared.isPresent() == rejection.isPresent()) {
                throw new IllegalArgumentException(
                        "exactly one execution-properties result branch is required");
            }
        }

        public static Result accepted(Prepared prepared) {
            return new Result(
                    Optional.of(Objects.requireNonNull(prepared, "prepared")),
                    Optional.empty());
        }

        public static Result rejected(String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "rejection reason must not be blank");
            }
            return new Result(Optional.empty(), Optional.of(reason));
        }
    }

    private record Compatibility(
            Optional<Boolean> value,
            Optional<String> rejection) {
        private Compatibility {
            value = Objects.requireNonNull(value, "value");
            rejection = Objects.requireNonNull(rejection, "rejection");
            if (value.isPresent() && rejection.isPresent()) {
                throw new IllegalArgumentException(
                        "compatibility cannot be accepted and rejected");
            }
        }

        private static Compatibility accepted(Optional<Boolean> value) {
            return new Compatibility(value, Optional.empty());
        }

        private static Compatibility rejected(String reason) {
            return new Compatibility(Optional.empty(), Optional.of(reason));
        }
    }
}
