package com.kltyton.autoseamblend.engine.routing.query;

import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import java.util.Objects;

/**
 * 中文：查询路由的六种可见来源；稳定回退不伪造一个文档所有者。
 * English: Six visible query-route sources; stable fallback never invents a document owner.
 */
public enum EngineRouteSource {
    NATIVE_AUTHOR,
    MANAGED_COMPATIBILITY,
    MANAGED_NON_COMPATIBILITY,
    CONFIG_COMPATIBILITY,
    CONFIG_NON_COMPATIBILITY,
    STABLE_FALLBACK;

    public static EngineRouteSource from(SourceTier tier) {
        return switch (Objects.requireNonNull(tier, "tier")) {
            case NATIVE_AUTHOR -> NATIVE_AUTHOR;
            case MANAGED_COMPATIBILITY -> MANAGED_COMPATIBILITY;
            case MANAGED_NON_COMPATIBILITY -> MANAGED_NON_COMPATIBILITY;
            case CONFIG_COMPATIBILITY -> CONFIG_COMPATIBILITY;
            case CONFIG_NON_COMPATIBILITY -> CONFIG_NON_COMPATIBILITY;
        };
    }

    /**
     * 中文：稳定回退只在需要统一优先级投影时作为最低配置级。
     * English: Stable fallback projects to the lowest config tier only for shared priority comparisons.
     */
    public SourceTier priorityTier() {
        return switch (this) {
            case NATIVE_AUTHOR -> SourceTier.NATIVE_AUTHOR;
            case MANAGED_COMPATIBILITY -> SourceTier.MANAGED_COMPATIBILITY;
            case MANAGED_NON_COMPATIBILITY -> SourceTier.MANAGED_NON_COMPATIBILITY;
            case CONFIG_COMPATIBILITY -> SourceTier.CONFIG_COMPATIBILITY;
            case CONFIG_NON_COMPATIBILITY, STABLE_FALLBACK -> SourceTier.CONFIG_NON_COMPATIBILITY;
        };
    }

    public boolean configOrFallback() {
        return this == CONFIG_COMPATIBILITY
                || this == CONFIG_NON_COMPATIBILITY
                || this == STABLE_FALLBACK;
    }
}
