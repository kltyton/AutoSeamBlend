package com.kltyton.autoseamblend.inference;

import com.kltyton.autoseamblend.engine.query.SurfaceFace;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：统一透明自消隐表面的 CTM 反向面补齐资格；Loader 只负责把结果写入自身 key/snapshot。
 * English: Centralizes CTM opposite-face eligibility for transparent self-culling surfaces;
 * Loaders only write the result into their native key/snapshot.
 */
public final class OppositeSurfaceCompletion {
    private OppositeSurfaceCompletion() {
    }

    /**
     * 中文：仅对 CTM、完整同状态边界事实和可支持几何返回缺失的反向面。
     * English: Returns a missing opposite face only for CTM, a suppressed equal-state boundary,
     * and supported geometry facts.
     */
    public static Optional<SurfaceFace> oppositeFace(
            SurfaceFace face,
            ConnectionMethod resolvedMethod,
            InferenceFacts facts,
            boolean equalStateBoundarySuppressed) {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        Objects.requireNonNull(facts, "facts");
        if (resolvedMethod != ConnectionMethod.CTM
                || !TransparentSelfConnectionInference.qualifies(
                        facts,
                        equalStateBoundarySuppressed)) {
            return Optional.empty();
        }
        SurfaceFace opposite = face.opposite();
        return opposite == SurfaceFace.UNDEFINED
                ? Optional.empty()
                : Optional.of(opposite);
    }
}
