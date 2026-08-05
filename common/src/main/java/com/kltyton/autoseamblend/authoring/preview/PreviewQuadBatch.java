package com.kltyton.autoseamblend.authoring.preview;

import java.util.List;
import java.util.Objects;

/**
 * 中文：带局部平移的冻结 Quad 批次；批次边界不携带引擎对象。
 *
 * English:
 * Frozen quad batch with a local translation; the batch boundary carries no
 * engine object.
 */
public record PreviewQuadBatch(
        List<PreviewQuad> quads,
        float x,
        float y,
        float z) {
    public PreviewQuadBatch {
        quads = List.copyOf(Objects.requireNonNull(quads, "quads"));
        if (quads.isEmpty()) {
            throw new IllegalArgumentException("preview quad batch must not be empty");
        }
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("preview batch translation must be finite");
        }
    }
}
