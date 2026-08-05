package com.kltyton.autoseamblend.compat.continuity.document;

import java.util.Objects;
import java.util.function.Consumer;
import me.pepperbell.continuity.api.client.CtmProperties;
import me.pepperbell.continuity.client.properties.BaseCtmProperties;

/**
 * 中文：共享 LoadingContainer 的 BaseCtmProperties 类型分支；Loader metadata 注册留在边界。
 * English: Shares the LoadingContainer BaseCtmProperties branch while keeping loader metadata
 * registration at the boundary.
 */
public final class ContinuityAcceptedHolderBridge {
    private ContinuityAcceptedHolderBridge() {}

    /**
     * 中文：仅对 Continuity 原生 BaseCtmProperties 执行接受回调。
     * English: Invokes the acceptance callback only for Continuity's native BaseCtmProperties.
     */
    public static void ifBaseProperties(
        CtmProperties properties,
            Consumer<BaseCtmProperties> accepted) {
        Objects.requireNonNull(accepted, "accepted");
        if (properties instanceof BaseCtmProperties base) {
            accepted.accept(base);
        }
    }
}
