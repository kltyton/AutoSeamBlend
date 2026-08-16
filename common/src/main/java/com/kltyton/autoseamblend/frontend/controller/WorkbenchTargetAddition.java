package com.kltyton.autoseamblend.frontend.controller;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import java.util.List;
import java.util.Objects;

/**
 * 中文：目标添加的幂等调和对账；重复添加同一接收方必须是无错误无修订变化的成功空操作。
 *
 * English: Idempotent reconciliation for target addition; repeating the same
 * receiver must be a successful no-op without revision growth or errors.
 */
public final class WorkbenchTargetAddition {
    private WorkbenchTargetAddition() {}

    /**
     * 中文：把请求的条目加入文档并从可用目标中移除该接收方；条目已存在时保持文档修订不变。
     *
     * English: Adds the requested item to the document and removes the receiver
     * from the available rows; an already-present item keeps the document
     * revision unchanged.
     */
    public static <T extends WorkbenchDraftFields> Result<T> reconcile(
            WorkbenchDocument<T> current,
            WorkbenchDocument.Item<T> requested,
            List<TargetRowView> availableTargets,
            String receiverBlockId) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(availableTargets, "availableTargets");
        Objects.requireNonNull(receiverBlockId, "receiverBlockId");
        WorkbenchDocument<T> reconciled = current.add(requested);
        boolean inserted = reconciled != current;
        List<TargetRowView> available = availableTargets.stream()
                .filter(row -> row.receiverBlockId()
                        .filter(receiverBlockId::equals)
                        .isEmpty())
                .toList();
        return new Result<>(reconciled, available, inserted);
    }

    /**
     * 中文：调和对账的不可变结果。
     *
     * English: Immutable reconciliation outcome.
     *
     * @param document 中文：已调和的文档；重复添加时与原文档同一实例。 / English: The reconciled document; the same instance as the input for a repeated add.
     * @param availableTargets 中文：不含该接收方的可用目标。 / English: Available rows without the receiver.
     * @param inserted 中文：本次是否实际插入了新条目。 / English: Whether a new item was inserted this time.
     */
    public record Result<T extends WorkbenchDraftFields>(
            WorkbenchDocument<T> document,
            List<TargetRowView> availableTargets,
            boolean inserted) {
        public Result {
            document = Objects.requireNonNull(document, "document");
            availableTargets = List.copyOf(
                    Objects.requireNonNull(
                            availableTargets,
                            "availableTargets"));
        }
    }
}
