package com.kltyton.autoseamblend.frontend.controller;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 中文：Loader 无关的目标添加协调。把请求的条目加入当前工作台文档，并把该接收方从
 * 可用目标行中移除；重复添加同一接收方是幂等成功 no-op（不增长修订号、不报错）。
 *
 * <p>English: Loader-neutral target addition reconciliation. Adds the requested item to the
 * current workbench document and removes that receiver from the available target rows;
 * repeating the same receiver is an idempotent successful no-op (no revision increase, no
 * error).
 */
public final class WorkbenchTargetAddition {
    private WorkbenchTargetAddition() {}

    /**
     * 中文：调和一次目标添加。可用行中接收方块等于 receiverBlockId 的行被无条件移除，
     * 因此扫描期残留的陈旧候选不会在重复添加时再次出现。
     *
     * <p>English: Reconciles one target addition. Available rows whose receiver block equals
     * receiverBlockId are removed unconditionally, so a stale scan-time candidate cannot
     * reappear for a repeated add.
     */
    public static <T extends WorkbenchDraftFields> Result<T> reconcile(
            WorkbenchDocument<T> document,
            WorkbenchDocument.Item<T> item,
            List<TargetRowView> availableTargets,
            String receiverBlockId) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(availableTargets, "availableTargets");
        Objects.requireNonNull(receiverBlockId, "receiverBlockId");
        if (receiverBlockId.isBlank()) {
            throw new IllegalArgumentException(
                    "receiverBlockId must not be blank");
        }
        WorkbenchDocument<T> reconciled = document.add(item);
        ArrayList<TargetRowView> available =
                new ArrayList<>(availableTargets.size());
        for (TargetRowView row : availableTargets) {
            if (row.receiverBlockId()
                    .filter(receiverBlockId::equals)
                    .isPresent()) {
                continue;
            }
            available.add(row);
        }
        return new Result<>(
                reconciled,
                List.copyOf(available),
                reconciled != document);
    }

    /**
     * 中文：调和后的文档、移除该接收方后的可用行，以及是否真正插入了新条目。
     *
     * <p>English: Reconciled document, available rows without that receiver, and whether a new
     * item was actually inserted.
     */
    public record Result<T extends WorkbenchDraftFields>(
            WorkbenchDocument<T> document,
            List<TargetRowView> availableTargets,
            boolean inserted) {
        public Result {
            Objects.requireNonNull(document, "document");
            availableTargets = List.copyOf(
                    Objects.requireNonNull(
                            availableTargets,
                            "availableTargets"));
        }
    }
}
