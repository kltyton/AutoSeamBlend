package com.kltyton.autoseamblend.discovery;

import java.util.Objects;

/**
 * 中文：同一精确表面的多个几何贡献者共享的代表面选择事实。
 * English: Loader-neutral representative-selection facts shared by multiple geometry contributors
 * of one exact surface.
 */
public record SurfaceRepresentativeFacts(
        boolean fullFace,
        int tintIndex) {
    public SurfaceRepresentativeFacts {
        if (tintIndex < -1) {
            throw new IllegalArgumentException("tintIndex must be -1 or non-negative");
        }
    }

    /**
     * 中文：完整面优先成为代表；色调索引按稳定输入顺序保留首个显式值。
     * English: Prefers a full-face representative and retains the first explicit tint index in
     * stable input order.
     */
    public SurfaceRepresentativeFacts merge(SurfaceRepresentativeFacts other) {
        SurfaceRepresentativeFacts candidate = Objects.requireNonNull(other, "other");
        return new SurfaceRepresentativeFacts(
                fullFace || candidate.fullFace,
                tintIndex >= 0 ? tintIndex : candidate.tintIndex);
    }

    /**
     * 中文：仅当当前代表不是完整面而候选是完整面时替换几何代表。
     * English: Replaces the geometry representative only when the current one is partial and the
     * candidate is a full face.
     */
    public boolean shouldReplaceWith(SurfaceRepresentativeFacts candidate) {
        SurfaceRepresentativeFacts other = Objects.requireNonNull(candidate, "candidate");
        return !fullFace && other.fullFace;
    }
}
