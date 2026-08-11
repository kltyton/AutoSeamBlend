package com.kltyton.autoseamblend.reload.rule;

import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：按一个原生文件记录 GUI 条目身份，不能与运行时方块目标主键混用。
 * English: Records one GUI entry identity per native document and is not a runtime target key.
 */
public record ManagedRuleDocument(
        EngineFamily family,
        String entryId,
        String documentPath,
        int order,
        List<String> targetBlockIds) {
    public ManagedRuleDocument {
        Objects.requireNonNull(family, "family");
        if (entryId == null || entryId.isEmpty()) {
            throw new IllegalArgumentException(
                    "invalid Managed entry id");
        }
        if (documentPath == null
                || documentPath.isBlank()
                || order < 0) {
            throw new IllegalArgumentException(
                    "invalid Managed document provenance");
        }
        targetBlockIds = List.copyOf(
                Objects.requireNonNull(targetBlockIds, "targetBlockIds"));
        if (targetBlockIds.stream().anyMatch(
                target -> target == null
                        || ResourceLocation.tryParse(target) == null)) {
            throw new IllegalArgumentException(
                    "invalid Managed document target");
        }
    }

    public String key() {
        return family.formatId() + ':' + documentPath;
    }
}
