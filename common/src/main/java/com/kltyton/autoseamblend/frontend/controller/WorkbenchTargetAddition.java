package com.kltyton.autoseamblend.frontend.controller;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import java.util.List;
import java.util.Objects;

/**
 * 中文：把目标添加定义为幂等工作台操作，并同时清除可能由增量扫描重新发布的候选行。
 *
 * <p>English: Defines target addition as an idempotent workbench operation and
 * removes candidate rows that an incremental scan may otherwise republish.
 */
public final class WorkbenchTargetAddition {
    private WorkbenchTargetAddition() {}

    public static <T extends WorkbenchDraftFields> Outcome<T> reconcile(
            WorkbenchDocument<T> current,
            WorkbenchDocument.Item<T> requested,
            List<TargetRowView> availableTargets,
            String receiverBlockId) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(availableTargets, "availableTargets");
        if (receiverBlockId == null || receiverBlockId.isBlank()) {
            throw new IllegalArgumentException("receiver block id must be nonblank");
        }
        WorkbenchDocument<T> document = current.add(requested);
        List<TargetRowView> remaining = availableTargets.stream()
                .filter(row -> row.receiverBlockId()
                        .filter(receiverBlockId::equals)
                        .isEmpty())
                .toList();
        return new Outcome<>(document, remaining, document != current);
    }

    public record Outcome<T extends WorkbenchDraftFields>(
            WorkbenchDocument<T> document,
            List<TargetRowView> availableTargets,
            boolean inserted) {
        public Outcome {
            document = Objects.requireNonNull(document, "document");
            availableTargets = List.copyOf(Objects.requireNonNull(
                    availableTargets,
                    "availableTargets"));
        }
    }
}
