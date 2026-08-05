package com.kltyton.autoseamblend.reload.rule;

import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：把四格式解析结果投影为公共规则/文档值对象；资源读取和槽位证据由 Loader 回调提供。
 *
 * English: Projects four-format parse results into common rule/document value
 * objects while Loader adapters provide resource reads and slot evidence.
 */
public final class ParsedRuleProjection {
    private ParsedRuleProjection() {}

    public static ManagedRuleDocument managedDocument(
            ParsedRuleDocument document,
            int order) {
        Objects.requireNonNull(document, "document");
        return new ManagedRuleDocument(
                document.family(),
                document.entryId(),
                document.documentPath(),
                order,
                document.targetBlockIds());
    }

    public static List<ManagedRule> managedRules(
            ParsedRuleDocument document,
            List<NativeSlot> slots,
            Optional<String> effectivePackId,
            int order) {
        Objects.requireNonNull(document, "document");
        List<NativeSlot> checkedSlots = List.copyOf(
                Objects.requireNonNull(slots, "slots"));
        Optional<String> checkedPackId = Objects.requireNonNull(
                effectivePackId,
                "effectivePackId");
        return document.targetBlockIds().stream()
                .map(target -> new ManagedRule(
                        document.family(),
                        target,
                        document.requestedMethod(),
                        document.compatibility(),
                        checkedSlots,
                        document.documentPath(),
                        document.resourceId(),
                        checkedPackId,
                        order))
                .toList();
    }

    public static List<NativeRule> nativeRules(
            ParsedRuleDocument document,
            List<NativeSlot> slots,
            String packId,
            int packPriority,
            int order) {
        Objects.requireNonNull(document, "document");
        List<NativeSlot> checkedSlots = List.copyOf(
                Objects.requireNonNull(slots, "slots"));
        Objects.requireNonNull(packId, "packId");
        return document.targetBlockIds().stream()
                .map(target -> new NativeRule(
                        document.family(),
                        target,
                        document.requestedMethod(),
                        document.compatibility(),
                        checkedSlots,
                        packId,
                        document.resourceId(),
                        packPriority,
                        order))
                .toList();
    }
}
