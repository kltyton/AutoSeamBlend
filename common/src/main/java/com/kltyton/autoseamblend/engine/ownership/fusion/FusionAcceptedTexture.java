package com.kltyton.autoseamblend.engine.ownership.fusion;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;

/**
 * 中文：不携带 Fusion 类型的已接受纹理文档 DTO，可安全进入平台 generation。
 *
 * <p>English: Accepted texture-document DTO with no Fusion types, safe for the platform
 * generation.
 */
public record FusionAcceptedTexture(
        String documentId,
        ConnectionMethod requestedMethod,
        ConnectionMethod resolvedMethod,
        boolean compatibility,
        int logicalSlotCount,
        List<String> exactSourceSpriteIds) {
    public FusionAcceptedTexture {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        if (resolvedMethod == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("resolvedMethod must be concrete");
        }
        if (requestedMethod != ConnectionMethod.AUTO && requestedMethod != resolvedMethod) {
            throw new IllegalArgumentException("manual method must equal resolved method");
        }
        if (logicalSlotCount < 0) {
            throw new IllegalArgumentException("logicalSlotCount must be non-negative");
        }
        exactSourceSpriteIds = List.copyOf(
                Objects.requireNonNull(exactSourceSpriteIds, "exactSourceSpriteIds"));
        if (exactSourceSpriteIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("source sprite ids must not be blank");
        }
    }
}
