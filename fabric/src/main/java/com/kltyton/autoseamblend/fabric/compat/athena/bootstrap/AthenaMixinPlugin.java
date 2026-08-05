package com.kltyton.autoseamblend.fabric.compat.athena.bootstrap;

import com.kltyton.autoseamblend.mixin.plugin.AbstractMixinConfigPlugin;
import com.kltyton.autoseamblend.mixin.plugin.MixinPluginTargets;
import com.kltyton.autoseamblend.mixin.plugin.MixinTargetRule;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 中文：Athena 未安装时阻止其可选 Mixin 解析。
 * English: Prevents optional Athena mixins from resolving when Athena is absent.
 */
public final class AthenaMixinPlugin
        extends AbstractMixinConfigPlugin {
    private static final MixinTargetRule TARGET_RULE =
            MixinPluginTargets.FABRIC_ATHENA;

    @Override
    protected MixinTargetRule targetRule() {
        return TARGET_RULE;
    }

    @Override
    protected boolean engineAvailable() {
        return FabricLoader.getInstance()
                .isModLoaded("athena");
    }
}
