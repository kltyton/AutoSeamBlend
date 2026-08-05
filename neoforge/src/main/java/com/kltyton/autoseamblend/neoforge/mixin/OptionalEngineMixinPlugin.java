package com.kltyton.autoseamblend.neoforge.mixin;

import com.kltyton.autoseamblend.mixin.plugin.AbstractMixinConfigPlugin;
import com.kltyton.autoseamblend.mixin.plugin.MixinPluginTargets;
import com.kltyton.autoseamblend.mixin.plugin.MixinTargetRule;

/**
 * 中文：精确目标不存在时阻止解析可选引擎 Mixin。 / English: Prevents optional engine mixins from resolving when exact targets are absent.
 */
public final class OptionalEngineMixinPlugin extends AbstractMixinConfigPlugin {
    private static final MixinTargetRule TARGET_RULE = MixinPluginTargets.NEOFORGE_OPTIONAL_ENGINE;

    @Override
    protected MixinTargetRule targetRule() {
        return TARGET_RULE;
    }

    @Override
    protected boolean engineAvailable() {
        return true;
    }
}
