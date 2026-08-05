package com.kltyton.autoseamblend.compat.athena.adapter;

import com.kltyton.autoseamblend.engine.plan.NativeMethodMapping;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;

/**
 * 中文：冻结 Athena 对公开 AutoSeamBlend 方法的逻辑映射；映射不依赖任一 Loader。
 * English: Freezes Athena's logical mapping for public AutoSeamBlend methods without depending
 * on either Loader.
 */
public final class AthenaMethodMapping {
    private AthenaMethodMapping() {}

    /**
     * 中文：保持 NeoForge 已接受的值合同，Fabric 仅通过薄桥调用本方法。
     * English: Preserves the user-accepted NeoForge value contract; Fabric calls this through a
     * thin bridge.
     */
    public static NativeMethodMapping nativeMapping(ConnectionMethod method) {
        return NativeMethodMapping.standard(
                method, value -> "athena:" + value.serializedName());
    }
}
