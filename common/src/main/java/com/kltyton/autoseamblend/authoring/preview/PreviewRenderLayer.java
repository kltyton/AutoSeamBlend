package com.kltyton.autoseamblend.authoring.preview;

/**
 * 中文：预览几何的项目自有渲染层；Loader 只在提交边界把它映射到原生渲染类型。
 *
 * English:
 * Project-owned render layers for preview geometry; a Loader maps them to
 * native render types only at the submission boundary.
 */
public enum PreviewRenderLayer {
    SOLID,
    CUTOUT,
    TRANSLUCENT,
    EMISSIVE;

    /**
     * 中文：从受控的资源层名称解析渲染层；emissive 信号优先，未知名称拒绝。
     *
     * English:
     * Resolves a render layer from a controlled resource-layer name; emissive
     * takes precedence and unknown names are rejected.
     */
    public static PreviewRenderLayer fromName(String sourceName, boolean emissive) {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("preview render layer name must not be blank");
        }
        PreviewRenderLayer base = switch (sourceName) {
            case "SOLID" -> SOLID;
            case "CUTOUT" -> CUTOUT;
            case "TRANSLUCENT" -> TRANSLUCENT;
            default -> throw new IllegalArgumentException(
                    "unsupported preview render layer name: " + sourceName);
        };
        return emissive ? EMISSIVE : base;
    }
}
