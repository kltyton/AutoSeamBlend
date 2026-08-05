package com.kltyton.autoseamblend.reload.rule;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;

/** 中文：一个原生作者文档针对单个方块的不可变规则投影。 / English: Immutable per-block rule projection of one native-author document. */
public record NativeRule(
        EngineFamily family,
        String targetBlockId,
        ConnectionMethod requestedMethod,
        boolean compatibility,
        List<NativeSlot> slots,
        String packId,
        String resourceId,
        int packPriority,
        int order) {
    public NativeRule {
        Objects.requireNonNull(family, "family");
        if (targetBlockId == null
                || Identifier.tryParse(targetBlockId) == null) {
            throw new IllegalArgumentException(
                    "invalid native target block id");
        }
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        if (packId == null
                || packId.isBlank()
                || resourceId == null
                || resourceId.isBlank()
                || order < 0) {
            throw new IllegalArgumentException(
                    "invalid native provenance");
        }
    }
}
