package com.kltyton.autoseamblend.reload.rule;

import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：规则快照中按引擎家族与规范方块 ID 分组的稳定键。
 * English: Stable grouping key for an engine family and canonical block id in rule snapshots.
 */
public record RuleTargetKey(
        EngineFamily family,
        String targetBlockId) {
    public RuleTargetKey {
        Objects.requireNonNull(family, "family");
        if (targetBlockId == null
                || ResourceLocation.tryParse(targetBlockId) == null) {
            throw new IllegalArgumentException(
                    "invalid rule target block id");
        }
    }
}
