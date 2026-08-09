package com.kltyton.autoseamblend.reload.rule;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** 中文：一个 Managed 原生文档针对单个方块的不可变规则投影。 / English: Immutable per-block rule projection of one Managed native document. */
public record ManagedRule(
        EngineFamily family,
        String targetBlockId,
        ConnectionMethod requestedMethod,
        boolean compatibility,
        List<NativeSlot> slots,
        String documentPath,
        String resourceId,
        Optional<String> effectivePackId,
        int order) {
    public ManagedRule {
        Objects.requireNonNull(family, "family");
        if (targetBlockId == null
                || ResourceLocation.tryParse(targetBlockId) == null) {
            throw new IllegalArgumentException(
                    "invalid Managed target block id");
        }
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        effectivePackId = Objects.requireNonNull(effectivePackId, "effectivePackId");
        effectivePackId.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException(
                        "invalid Managed effective pack identity");
            }
        });
        if (documentPath == null
                || documentPath.isBlank()
                || resourceId == null
                || resourceId.isBlank()
                || order < 0) {
            throw new IllegalArgumentException(
                    "invalid Managed provenance");
        }
    }
}
