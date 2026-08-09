package com.kltyton.autoseamblend.fabric.compat.fusion.bootstrap;

import com.kltyton.autoseamblend.mixin.plugin.AbstractMixinConfigPlugin;
import com.kltyton.autoseamblend.mixin.plugin.MixinPluginSelector;
import com.kltyton.autoseamblend.mixin.plugin.MixinPluginTargets;
import com.kltyton.autoseamblend.mixin.plugin.MixinTargetRule;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * 中文：仅在 Fusion 实际版本精确为 1.3.12 时启用可选 Fusion Mixin；缺失或版本不符时阻止解析。
 * English: Enables the optional Fusion mixins only when the installed Fusion version is exactly
 * 1.3.12; blocks resolution when Fusion is absent or the version mismatches.
 */
public final class FusionMixinPlugin
        extends AbstractMixinConfigPlugin {
    private static final MixinTargetRule TARGET_RULE =
            MixinPluginTargets.FABRIC_FUSION;

    private static final String REQUIRED_FUSION_VERSION = "1.3.12";

    @Override
    protected MixinTargetRule targetRule() {
        return TARGET_RULE;
    }

    @Override
    protected boolean engineAvailable() {
        Optional<String> availableVersion = FabricLoader.getInstance()
                .getModContainer("fusion")
                .map(ModContainer::getMetadata)
                .map(ModMetadata::getVersion)
                .map(Version::getFriendlyString);
        return MixinPluginSelector.exactVersion(
                availableVersion,
                REQUIRED_FUSION_VERSION);
    }
}
