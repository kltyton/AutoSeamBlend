package com.kltyton.autoseamblend.mixin.plugin;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 中文：描述可选引擎 Mixin 的不可变目标规则。 / English: Immutable target rule for optional-engine mixins.
 *
 * @param exactTargets 中文：完整目标类名。 / English: Exact target class names.
 * @param targetPrefixes 中文：目标类名前缀。 / English: Target class-name prefixes.
 * @param requireTargetClass 中文：是否要求目标类资源存在。 / English: Whether the target class resource must exist.
 */
public record MixinTargetRule(
        Set<String> exactTargets,
        Set<String> targetPrefixes,
        boolean requireTargetClass) {

    public MixinTargetRule {
        exactTargets = immutableNames(exactTargets, "exactTargets");
        targetPrefixes = immutableNames(targetPrefixes, "targetPrefixes");
    }

    /** 中文：创建匹配所有目标的规则。 / English: Creates a rule matching every target. */
    public static MixinTargetRule any(boolean requireTargetClass) {
        return new MixinTargetRule(Set.of(), Set.of(), requireTargetClass);
    }

    /**
     * 中文：创建精确目标规则。 / English: Creates an exact-target rule.
     */
    public static MixinTargetRule exact(boolean requireTargetClass, String... targets) {
        return new MixinTargetRule(Set.of(targets), Set.of(), requireTargetClass);
    }

    /**
     * 中文：创建前缀目标规则。 / English: Creates a target-prefix rule.
     */
    public static MixinTargetRule prefix(boolean requireTargetClass, String... prefixes) {
        return new MixinTargetRule(Set.of(), Set.of(prefixes), requireTargetClass);
    }

    /**
     * 中文：创建同时包含精确名和前缀的规则。 / English: Creates a rule with exact names and prefixes.
     */
    public static MixinTargetRule of(
            boolean requireTargetClass, Set<String> exactTargets, Set<String> targetPrefixes) {
        return new MixinTargetRule(exactTargets, targetPrefixes, requireTargetClass);
    }

    /**
     * 中文：判断目标类名是否被规则选择。 / English: Tests whether a target class name is selected.
     */
    public boolean matches(String targetClassName) {
        if (targetClassName == null) {
            return false;
        }
        if (exactTargets.isEmpty() && targetPrefixes.isEmpty()) {
            return true;
        }
        if (exactTargets.contains(targetClassName)) {
            return true;
        }
        return targetPrefixes.stream().anyMatch(targetClassName::startsWith);
    }

    private static Set<String> immutableNames(Set<String> names, String fieldName) {
        if (names == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        var copy = new LinkedHashSet<String>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must contain non-blank names");
            }
            copy.add(name);
        }
        return Set.copyOf(copy);
    }
}
