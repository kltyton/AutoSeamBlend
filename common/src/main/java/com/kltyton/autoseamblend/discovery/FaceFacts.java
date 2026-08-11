package com.kltyton.autoseamblend.discovery;

import java.util.Objects;

/** 中文：一个烘焙模型面的可复现事实；Loader 或引擎对象不会越过此边界。 / English: Reproducible facts for one baked model face; no loader or engine object crosses this boundary. */
public record FaceFacts(
        String targetId,
        String stateKey,
        Face face,
        String spriteId,
        float minU,
        float minV,
        float maxU,
        float maxV,
        int textureWidth,
        int textureHeight,
        boolean fullBlock,
        boolean axisAligned,
        boolean fullFace,
        boolean opaque,
        boolean animated,
        boolean tinted,
        int resourcePackPriority,
        NativeOwnership nativeOwnership) {
    public static final int UNKNOWN_RESOURCE_PACK_PRIORITY = Integer.MIN_VALUE;

    public FaceFacts {
        targetId = requireText(targetId, "targetId");
        stateKey = requireText(stateKey, "stateKey");
        face = Objects.requireNonNull(face, "face");
        spriteId = requireText(spriteId, "spriteId");
        if (!Float.isFinite(minU) || !Float.isFinite(minV)
                || !Float.isFinite(maxU) || !Float.isFinite(maxV)
                || minU > maxU || minV > maxV) {
            throw new IllegalArgumentException("UV bounds must be finite and ordered");
        }
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("texture dimensions must be positive");
        }
        nativeOwnership = Objects.requireNonNull(nativeOwnership, "nativeOwnership");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    public enum Face {
        DOWN,
        UP,
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    /** 中文：发现结果是查询前证据；精确的已接受所有权只在渲染查询时解析。 / English: Discovery is pre-query evidence; exact accepted ownership is resolved only at render-query time. */
    public enum NativeOwnership {
        UNKNOWN,
        NONE,
        ACCEPTED
    }
}
