package com.kltyton.autoseamblend.authoring.preview;

/**
 * 中文：一个纹理来源在规范化预览面中的不可变区域图层，不包含 Loader 精灵类型。
 *
 * English: Immutable normalized face region for one texture source, without a
 * Loader sprite type.
 */
public record PreviewFaceLayer(
        String sourceKey,
        float x0,
        float y0,
        float x1,
        float y1,
        float u0,
        float v0,
        float u1,
        float v1,
        int tint) {
    public PreviewFaceLayer {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException(
                    "preview sourceKey must not be blank");
        }
        validateRange(x0, x1, "x");
        validateRange(y0, y1, "y");
        validateRange(u0, u1, "u");
        validateRange(v0, v1, "v");
    }

    public static PreviewFaceLayer full(
            String sourceKey,
            int tint) {
        return new PreviewFaceLayer(
                sourceKey,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                tint);
    }

    private static void validateRange(
            float start,
            float end,
            String axis) {
        if (!Float.isFinite(start)
                || !Float.isFinite(end)
                || start < 0.0F
                || end > 1.0F
                || start >= end) {
            throw new IllegalArgumentException(
                    axis
                            + " coordinates must form a normalized range");
        }
    }
}
