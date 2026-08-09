package com.kltyton.autoseamblend.mixin.plugin;

import java.util.Optional;

/**
 * 中文：集中执行可选引擎 Mixin 的版本、目标和类存在性判定。 / English: Centralizes optional-engine version, target, and class-presence checks.
 */
public final class MixinPluginSelector {
    private MixinPluginSelector() {}

    /**
     * 中文：判断精确版本是否可用。 / English: Checks an exact engine version.
     *
     * <p>Loader-specific code supplies the version lookup; the common module owns the equality
     * policy so every plugin uses the same rule. / Loader 专属代码提供版本查询；common 统一负责相等策略。
     */
    public static boolean exactVersion(Optional<String> availableVersion, String requiredVersion) {
        return availableVersion != null
                && requiredVersion != null
                && availableVersion.filter(requiredVersion::equals).isPresent();
    }

    /**
     * 中文：按不可变规则决定是否应用 Mixin。 / English: Decides whether a Mixin should apply under an immutable rule.
     */
    public static boolean shouldApply(
            MixinTargetRule rule, boolean engineAvailable, String targetClassName, ClassLoader loader) {
        if (rule == null || !engineAvailable || !rule.matches(targetClassName)) {
            return false;
        }
        return !rule.requireTargetClass() || classPresent(loader, targetClassName);
    }

    /**
     * 中文：仅通过资源查找判断类是否已链接，避免主动加载可选引擎类型。 / English: Checks class linkage through a resource lookup without loading optional-engine types.
     */
    public static boolean classPresent(ClassLoader loader, String targetClassName) {
        if (loader == null || targetClassName == null || targetClassName.isBlank()) {
            return false;
        }
        String targetResource = targetClassName.replace('.', '/') + ".class";
        return loader.getResource(targetResource) != null;
    }
}
