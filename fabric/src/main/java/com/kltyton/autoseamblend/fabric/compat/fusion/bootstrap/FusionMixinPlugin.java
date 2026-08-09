package com.kltyton.autoseamblend.fabric.compat.fusion.bootstrap;

import com.kltyton.autoseamblend.fabric.engine.registry.FabricEngineRegistry;
import com.kltyton.autoseamblend.mixin.plugin.AbstractMixinConfigPlugin;
import com.kltyton.autoseamblend.mixin.plugin.MixinPluginSelector;
import com.kltyton.autoseamblend.mixin.plugin.MixinPluginTargets;
import com.kltyton.autoseamblend.mixin.plugin.MixinTargetRule;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 中文：Fusion 缺失或版本不精确匹配锁定时阻止其可选 Mixin 解析。
 * English: Prevents optional Fusion mixins from resolving when Fusion is
 * absent or its version does not exactly match the locked pin.
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
        return MixinPluginSelector.exactVersion(
                FabricLoader.getInstance()
                        .getModContainer("fusion")
                        .map(container ->
                                container.getMetadata()
                                        .getVersion()
                                        .getFriendlyString()),
                FabricEngineRegistry.expectedVersion("fusion"));
    }
}
