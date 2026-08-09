package com.kltyton.autoseamblend.engine.ownership.fusion;

import com.kltyton.autoseamblend.engine.query.ExactSurfaceIdentity;
import java.util.Objects;

/**
 * 中文：绑定 reload token、精确 surface 和遍历序号的 Fusion 证据键。
 * <p>
 * English: Loader-neutral Fusion evidence key bound to a reload token, exact surface, and
 * traversal ordinal.
 */
public record FusionExactEvidenceKey(
        long tokenOrdinal,
        ExactSurfaceIdentity identity,
        int ordinal) {
    public FusionExactEvidenceKey {
        if (tokenOrdinal <= 0 || ordinal < 0) {
            throw new IllegalArgumentException("invalid exact-evidence key");
        }
        Objects.requireNonNull(identity, "identity");
    }
}
