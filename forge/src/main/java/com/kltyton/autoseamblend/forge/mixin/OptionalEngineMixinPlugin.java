package com.kltyton.autoseamblend.forge.mixin;

import com.kltyton.autoseamblend.mixin.plugin.AbstractMixinConfigPlugin;
import com.kltyton.autoseamblend.mixin.plugin.MixinPluginTargets;
import com.kltyton.autoseamblend.mixin.plugin.MixinTargetRule;
import com.kltyton.autoseamblend.forge.engine.registry.ForgeEngineRegistry;
import java.util.Optional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;

/**
 * 中文：引擎缺失、版本不精确匹配锁定或精确目标不存在时阻止解析可选引擎 Mixin。
 * English: Prevents optional engine mixins from resolving when the engine is
 * absent, its version does not exactly match the locked pin, or exact targets
 * are absent.
 */
public final class OptionalEngineMixinPlugin extends AbstractMixinConfigPlugin {
    private static final MixinTargetRule TARGET_RULE = MixinPluginTargets.NEOFORGE_OPTIONAL_ENGINE;
    private String engineId;

    @Override
    public void onLoad(String mixinPackage) {
        engineId = engineIdFor(mixinPackage);
    }

    @Override
    protected MixinTargetRule targetRule() {
        return TARGET_RULE;
    }

    @Override
    protected boolean engineAvailable() {
        return engineId != null
                && engineVersion(engineId)
                        .filter(version -> ForgeEngineRegistry.acceptsVersion(
                                engineId, version))
                        .isPresent();
    }

    /**
     * 中文：Mixin prepare 阶段 FML ModList 尚未初始化时回退到加载期 ModList，避免启动 NPE。
     * English: Falls back to the loading ModList while the FML ModList is not yet initialized
     * during Mixin prepare, preventing a startup NPE.
     */
    private static Optional<String> engineVersion(String engineId) {
        Optional<String> runtimeVersion = Optional.empty();
        ModList modList = ModList.get();
        if (modList != null) {
            runtimeVersion = modList.getModContainerById(engineId)
                    .map(container ->
                            container.getModInfo()
                                    .getVersion()
                                    .toString());
        }

        Optional<String> loadingVersion = Optional.empty();
        LoadingModList loadingModList = FMLLoader.getLoadingModList();
        if (loadingModList != null) {
            loadingVersion = loadingModList.getMods().stream()
                    .filter(modInfo -> engineId.equals(modInfo.getModId()))
                    .map(modInfo -> modInfo.getVersion().toString())
                    .findFirst();
        }
        return selectVersion(runtimeVersion, loadingVersion);
    }

    /**
     * 中文：运行期列表尚未填充时必须回退到加载期列表；运行期结果存在时保持优先。
     * English: Falls back to the loading list while the runtime list is not populated,
     * while preserving a present runtime result as authoritative.
     */
    static Optional<String> selectVersion(
            Optional<String> runtimeVersion,
            Optional<String> loadingVersion) {
        return runtimeVersion.or(() -> loadingVersion);
    }

    private static String engineIdFor(String mixinPackage) {
        if (mixinPackage == null) {
            return null;
        }
        if (mixinPackage.contains(".continuity")) {
            return "continuity";
        }
        if (mixinPackage.contains(".ctm")) {
            return "ctm";
        }
        if (mixinPackage.contains(".fusion")) {
            return "fusion";
        }
        if (mixinPackage.contains(".athena")) {
            return "athena";
        }
        return null;
    }
}
