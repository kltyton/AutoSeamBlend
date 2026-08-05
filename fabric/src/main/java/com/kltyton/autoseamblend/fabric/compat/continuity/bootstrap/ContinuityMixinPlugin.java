package com.kltyton.autoseamblend.fabric.compat.continuity.bootstrap;

import com.kltyton.autoseamblend.mixin.plugin.AbstractMixinConfigPlugin;
import com.kltyton.autoseamblend.mixin.plugin.MixinPluginTargets;
import com.kltyton.autoseamblend.mixin.plugin.MixinTargetRule;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 中文：Continuity 未安装时阻止其可选 Mixin 解析。
 * English: Prevents optional Continuity mixins from resolving when Continuity is absent.
 */
public final class ContinuityMixinPlugin
        extends AbstractMixinConfigPlugin {
    private static final MixinTargetRule TARGET_RULE =
            MixinPluginTargets.FABRIC_CONTINUITY;

    @Override
    protected MixinTargetRule targetRule() {
        return TARGET_RULE;
    }

    @Override
    protected boolean engineAvailable() {
        return FabricLoader.getInstance()
                .isModLoaded("continuity");
    }
}
