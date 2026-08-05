package com.kltyton.autoseamblend.fabric.compat.fusion.bootstrap;

import com.kltyton.autoseamblend.mixin.plugin.AbstractMixinConfigPlugin;
import com.kltyton.autoseamblend.mixin.plugin.MixinPluginTargets;
import com.kltyton.autoseamblend.mixin.plugin.MixinTargetRule;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 中文：Fusion 未安装时阻止其可选 Mixin 解析。
 * English: Prevents optional Fusion mixins from resolving when Fusion is absent.
 */
public final class FusionMixinPlugin
        extends AbstractMixinConfigPlugin {
    private static final MixinTargetRule TARGET_RULE =
            MixinPluginTargets.FABRIC_FUSION;

    @Override
    protected MixinTargetRule targetRule() {
        return TARGET_RULE;
    }

    @Override
    protected boolean engineAvailable() {
        return FabricLoader.getInstance()
                .isModLoaded("fusion");
    }
}
