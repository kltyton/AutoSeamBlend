package com.kltyton.autoseamblend.engine.ownership.evidence;

import java.util.Optional;

/**
 * 中文：向公共证据解析器注入只读资源与纹理表观察，不泄漏 Loader 资源类型。
 *
 * English: Injects read-only resources and sheet observations into common evidence resolvers
 * without leaking Loader resource types.
 */
public interface NativeResourceSource {
    Optional<byte[]> read(String resourceId);

    TextureResourceState inspectTexture(
            String spriteId,
            int columns,
            int rows,
            SheetFramePolicy policy);

    enum TextureResourceState {
        PRESENT,
        MISSING,
        INVALID
    }

    enum SheetFramePolicy {
        EXISTENCE_ONLY,
        STANDARD,
        FUSION
    }
}
