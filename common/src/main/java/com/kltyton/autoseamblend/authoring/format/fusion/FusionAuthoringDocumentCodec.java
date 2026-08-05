package com.kltyton.autoseamblend.authoring.format.fusion;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;

/**
 * 中文：只编辑 Fusion JSON 的两个 AutoSeamBlend 扩展，不转换为其他引擎格式。
 *
 * <p>English: Edits only the two AutoSeamBlend extensions in Fusion JSON and never converts it
 * to another engine's format.
 */
public final class FusionAuthoringDocumentCodec {
    public FusionNativeDocument read(String json) {
        return FusionNativeDocument.parse(json);
    }

    public String authoring(
            FusionNativeDocument source,
            ConnectionMethod requestedMethod,
            boolean compatibility) {
        return Objects.requireNonNull(source, "source")
                .withExtensions(
                        Objects.requireNonNull(requestedMethod, "requestedMethod"),
                        compatibility)
                .encode();
    }

    /**
     * 中文：生成仅删除项目扩展的原生文档视图；物理布局和 PNG 由 Fusion baked 管线补齐。
     * English: Produces a native document view with only project extensions removed; the Fusion
     * baked pipeline completes the physical layout and PNG.
     */
    public String nativeDocumentWithoutExtensions(FusionNativeDocument source) {
        return Objects.requireNonNull(source, "source")
                .withoutProjectExtensions()
                .encode();
    }
}
