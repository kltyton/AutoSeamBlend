package com.kltyton.autoseamblend.authoring.format.fusion;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;

/**
 * 中文：工作台边界的 Fusion 原生 JSON 编解码器，只保留 AutoSeamBlend 扩展。
 *
 * English: Fusion-native JSON codec at the workbench boundary; it preserves only the
 * AutoSeamBlend extensions this adapter owns.
 */
public final class FusionWorkbenchNativeDocumentCodec {
    public FusionNativeDocument read(String json) {
        return FusionNativeDocument.parse(json);
    }

    public String authoring(
            FusionNativeDocument source,
            ConnectionMethod requestedMethod,
            boolean compatibility) {
        return source.withExtensions(requestedMethod, compatibility).encode();
    }

    public String nativeDocumentWithoutExtensions(FusionNativeDocument source) {
        return source.withoutProjectExtensions().encode();
    }
}
