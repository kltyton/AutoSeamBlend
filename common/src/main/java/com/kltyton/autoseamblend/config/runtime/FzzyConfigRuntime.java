package com.kltyton.autoseamblend.config.runtime;

import com.kltyton.autoseamblend.config.model.ConfigSnapshot;
import com.kltyton.autoseamblend.config.schema.FzzyAutoSeamBlendConfig;

/**
 * 中文：负责 Fzzy 配置的注册、加载和不可变选择器快照冻结。
 *
 * English: Owns Fzzy configuration registration, loading, and immutable selector snapshot
 * freezing.
 */
public final class FzzyConfigRuntime {
    private FzzyConfigRuntime() {}

    public static void initialize() {
        FzzyAutoSeamBlendConfig.registerAndLoad();
    }

    public static ConfigSnapshot current() {
        FzzyAutoSeamBlendConfig config = FzzyAutoSeamBlendConfig.active();
        return ConfigSnapshot.capture(
                config.automaticDiscovery.get(),
                config.targets,
                config.excludedTargets);
    }
}
