package com.kltyton.autoseamblend.texture.budget;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 中文：校验单个纹理输入载体的原始字节长度，并在读取时保持有界。
 * English: Validates one texture-input carrier's raw byte length and keeps reads bounded.
 */
public final class TextureInputBudget {
    /** 中文：PNG 输入的最大字节数。 / English: Maximum byte count for a PNG input. */
    public static final long MAX_PNG_BYTES = 67_108_864L;
    /** 中文：原生文档输入的最大字节数。 / English: Maximum byte count for a native document input. */
    public static final long MAX_NATIVE_DOCUMENT_BYTES = 4_194_304L;
    /** 中文：元数据输入的最大字节数。 / English: Maximum byte count for metadata input. */
    public static final long MAX_METADATA_BYTES = 1_048_576L;

    /** 中文：无状态的默认预算实例。 / English: The stateless default budget instance. */
    public static final TextureInputBudget DEFAULT = new TextureInputBudget();

    private TextureInputBudget() {
    }

    /**
     * 中文：输入载体的类别及其最大原始字节数。
     * English: Input-carrier kinds and their maximum raw byte counts.
     */
    public enum InputKind {
        PNG(MAX_PNG_BYTES),
        NATIVE_DOCUMENT(MAX_NATIVE_DOCUMENT_BYTES),
        METADATA(MAX_METADATA_BYTES);

        private final long limit;

        InputKind(long limit) {
            this.limit = limit;
        }

        /** 中文：返回该类别的字节上限。 / English: Returns this kind's byte limit. */
        public long limit() {
            return limit;
        }
    }

    /**
     * 中文：从调用方提供的流读取至多 limit+1 字节，以便确定性拒绝超限输入；不会关闭调用方的流。
     * English: Reads at most limit+1 bytes so oversized input is deterministically rejected; the
     * caller-owned stream is not closed.
     */
    public byte[] read(InputStream input, InputKind kind, String scope) throws IOException {
        Objects.requireNonNull(input, "input");
        InputKind checkedKind = Objects.requireNonNull(kind, "kind");
        String checkedScope = Objects.requireNonNull(scope, "scope");
        int probeLength = Math.toIntExact(checkedKind.limit() + 1L);
        byte[] bytes = input.readNBytes(probeLength);
        if (bytes.length > checkedKind.limit()) {
            throw violation(
                    ViolationCode.INPUT_BYTES_EXCEEDED,
                    checkedKind,
                    checkedScope,
                    bytes.length,
                    checkedKind.limit());
        }
        return bytes;
    }

    /**
     * 中文：先按文件大小拒绝明显超限输入，再在受控范围内读取并关闭本方法打开的文件流。
     * English: Rejects an obviously oversized file before opening it, then reads it while closing
     * the file stream opened by this method.
     */
    public byte[] read(Path path, InputKind kind, String scope) throws IOException {
        Path checkedPath = Objects.requireNonNull(path, "path");
        InputKind checkedKind = Objects.requireNonNull(kind, "kind");
        String checkedScope = Objects.requireNonNull(scope, "scope");
        requireLength(checkedKind, Files.size(checkedPath), checkedScope);
        try (InputStream input = Files.newInputStream(checkedPath)) {
            return read(input, checkedKind, checkedScope);
        }
    }

    /**
     * 中文：校验单个输入载体的长度；负数和超过类别上限的长度均结构化拒绝。
     * English: Validates one input-carrier length; negative and over-limit lengths are rejected
     * with structured diagnostics.
     */
    public void requireLength(InputKind kind, long length, String scope) {
        InputKind checkedKind = Objects.requireNonNull(kind, "kind");
        String checkedScope = Objects.requireNonNull(scope, "scope");
        long limit = checkedKind.limit();
        if (length < 0L) {
            throw violation(
                    ViolationCode.INPUT_LENGTH_NEGATIVE,
                    checkedKind,
                    checkedScope,
                    length,
                    limit);
        }
        if (length > limit) {
            throw violation(
                    ViolationCode.INPUT_BYTES_EXCEEDED,
                    checkedKind,
                    checkedScope,
                    length,
                    limit);
        }
    }

    /** 中文：输入预算的稳定诊断码。 / English: Stable diagnostic codes for input-budget violations. */
    public enum ViolationCode {
        INPUT_LENGTH_NEGATIVE,
        INPUT_BYTES_EXCEEDED
    }

    /**
     * 中文：不可变的输入预算诊断值。
     * English: Immutable input-budget diagnostic value.
     */
    public record Violation(
            ViolationCode code,
            InputKind kind,
            String scope,
            long observed,
            long limit) {
        public Violation {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(scope, "scope");
            if (limit < 0L) {
                throw new IllegalArgumentException("limit must be non-negative");
            }
        }
    }

    /**
     * 中文：保留结构化诊断并提供稳定异常消息。
     * English: Carries structured diagnostics and provides a stable exception message.
     */
    public static final class ViolationException extends IllegalArgumentException {
        private final Violation violation;

        private ViolationException(Violation violation) {
            super(format(violation));
            this.violation = violation;
        }

        /** 中文：返回不可变诊断值。 / English: Returns the immutable diagnostic value. */
        public Violation violation() {
            return violation;
        }

        private static String format(Violation violation) {
            return "TEXTURE_INPUT_BUDGET_VIOLATION:"
                    + violation.code()
                    + ":kind=" + violation.kind()
                    + ":scope=" + violation.scope()
                    + ":observed=" + violation.observed()
                    + ":limit=" + violation.limit();
        }
    }

    private ViolationException violation(
            ViolationCode code,
            InputKind kind,
            String scope,
            long observed,
            long limit) {
        return new ViolationException(new Violation(code, kind, scope, observed, limit));
    }
}
