package com.kltyton.autoseamblend.mixin.plugin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * 中文：提供所有可选引擎插件共用的空生命周期和选择逻辑。 / English: Shared no-op lifecycle and selection logic for optional-engine plugins.
 */
public abstract class AbstractMixinConfigPlugin implements IMixinConfigPlugin {
    @Override
    public final void onLoad(String mixinPackage) {}

    @Override
    public final String getRefMapperConfig() {
        return null;
    }

    @Override
    public final boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return MixinPluginSelector.shouldApply(
                targetRule(), engineAvailable(), targetClassName, getClass().getClassLoader());
    }

    @Override
    public final void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public final List<String> getMixins() {
        return null;
    }

    @Override
    public final void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {}

    @Override
    public final void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {}

    /** 中文：返回不可变目标规则。 / English: Returns the immutable target rule. */
    protected abstract MixinTargetRule targetRule();

    /** 中文：返回 Loader 侧引擎门禁。 / English: Returns the loader-side engine gate. */
    protected abstract boolean engineAvailable();
}
