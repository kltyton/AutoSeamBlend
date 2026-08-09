package com.kltyton.autoseamblend.texture.budget;

import java.util.Objects;

/**
 * 中文：单张纹理输入和生成图像共用的不可变安全预算与校验器。
 * English: Immutable safety budget and validator shared by one source or generated image.
 *
 * <p>The limits intentionally mirror the accepted NeoForge 26.1.2 baseline and apply only to
 * one image at a time; aggregate generation limits remain outside this type.</p>
 */
public final class TextureImageBudget {
    /** 中文：允许的单轴最大尺寸。 / English: Maximum size of either image axis. */
    public static final int MAX_DIMENSION = 4096;
    /** 中文：允许的单图像素总数。 / English: Maximum number of pixels in one image. */
    public static final int MAX_PIXELS = 16_777_216;
    /** 中文：允许的原始元数据字节数。 / English: Maximum raw metadata byte count. */
    public static final int MAX_METADATA_BYTES = 1_048_576;

    /** 中文：已接受基线预算。 / English: Accepted baseline budget. */
    public static final TextureImageBudget DEFAULT = new TextureImageBudget();

    private TextureImageBudget() {
    }

    /**
     * 中文：校验图像和动画帧布局，并返回可安全用于 int[] 分配的像素数。
     * English: Validates image and animation-frame geometry and returns the pixel count safe for
     * an int[] allocation.
     */
    public int requireImage(
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight) {
        requireDimension(sheetWidth);
        requireDimension(sheetHeight);
        long pixels = Math.multiplyExact((long) sheetWidth, (long) sheetHeight);
        if (pixels > MAX_PIXELS) {
            throw violation(
                    ViolationCode.PIXEL_COUNT_EXCEEDED,
                    pixels,
                    MAX_PIXELS);
        }
        requireFrameDimension(frameWidth);
        requireFrameDimension(frameHeight);
        if (sheetWidth % frameWidth != 0) {
            throw violation(
                    ViolationCode.FRAME_WIDTH_NOT_DIVISIBLE,
                    sheetWidth,
                    frameWidth);
        }
        if (sheetHeight % frameHeight != 0) {
            throw violation(
                    ViolationCode.FRAME_HEIGHT_NOT_DIVISIBLE,
                    sheetHeight,
                    frameHeight);
        }
        return Math.toIntExact(pixels);
    }

    /**
     * 中文：校验一维像素数组长度；调用方必须在此校验后再 clone。
     * English: Validates a one-dimensional pixel-array length; callers must clone only after this
     * check.
     */
    public void requirePixelArrayLength(int actualLength, int expectedLength) {
        if (actualLength != expectedLength) {
            throw violation(
                    ViolationCode.PIXEL_ARRAY_LENGTH_MISMATCH,
                    actualLength,
                    expectedLength);
        }
    }

    /**
     * 中文：校验原始元数据长度；该预算只约束单个元数据载体。
     * English: Validates raw metadata length; this budget applies to one metadata carrier only.
     */
    public void requireMetadataLength(int length) {
        if (length < 0) {
            throw violation(
                    ViolationCode.METADATA_LENGTH_NEGATIVE,
                    length,
                    0L);
        }
        if (length > MAX_METADATA_BYTES) {
            throw violation(
                    ViolationCode.METADATA_BYTES_EXCEEDED,
                    length,
                    MAX_METADATA_BYTES);
        }
    }

    /** 中文：为现有调用链构造稳定的异常诊断。 / English: Builds stable exception diagnostics for existing call chains. */
    private ViolationException violation(
            ViolationCode code,
            long observed,
            long limit) {
        return new ViolationException(new Violation(code, observed, limit));
    }

    private void requireDimension(int value) {
        if (value <= 0) {
            throw violation(
                    ViolationCode.DIMENSION_NON_POSITIVE,
                    value,
                    1L);
        }
        if (value > MAX_DIMENSION) {
            throw violation(
                    ViolationCode.DIMENSION_EXCEEDED,
                    value,
                    MAX_DIMENSION);
        }
    }

    private void requireFrameDimension(int value) {
        if (value <= 0) {
            throw violation(
                    ViolationCode.FRAME_DIMENSION_NON_POSITIVE,
                    value,
                    1L);
        }
    }

    /** 中文：违反单图预算的稳定诊断码。 / English: Stable diagnostic codes for per-image budget violations. */
    public enum ViolationCode {
        DIMENSION_NON_POSITIVE,
        DIMENSION_EXCEEDED,
        PIXEL_COUNT_EXCEEDED,
        FRAME_DIMENSION_NON_POSITIVE,
        FRAME_WIDTH_NOT_DIVISIBLE,
        FRAME_HEIGHT_NOT_DIVISIBLE,
        PIXEL_ARRAY_LENGTH_MISMATCH,
        METADATA_LENGTH_NEGATIVE,
        METADATA_BYTES_EXCEEDED
    }

    /** 中文：不可变的 code/observed/limit 三元组。 / English: Immutable code/observed/limit diagnostic tuple. */
    public record Violation(
            ViolationCode code,
            long observed,
            long limit) {
        public Violation {
            Objects.requireNonNull(code, "code");
        }
    }

    /**
     * 中文：保留结构化预算诊断，同时兼容现有 IllegalArgumentException 调用链。
     * English: Retains structured budget diagnostics while remaining compatible with existing
     * IllegalArgumentException call chains.
     */
    public static final class ViolationException extends IllegalArgumentException {
        private final Violation violation;

        private ViolationException(Violation violation) {
            super(format(violation));
            this.violation = violation;
        }

        public Violation violation() {
            return violation;
        }

        private static String format(Violation violation) {
            return "TEXTURE_IMAGE_BUDGET_VIOLATION:" + violation.code()
                    + ":observed=" + violation.observed()
                    + ":limit=" + violation.limit();
        }
    }
}
