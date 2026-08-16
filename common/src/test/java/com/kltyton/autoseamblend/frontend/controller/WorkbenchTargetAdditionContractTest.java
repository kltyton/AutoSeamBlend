package com.kltyton.autoseamblend.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * 中文：共享 TARGET_ALREADY_PRESENT 缺陷的行为回归。首次 reconcile 插入条目并增长修订号、
 * 移除陈旧候选行；对同一接收方的第二次 reconcile（陈旧候选仍在可用列表内）必须报告
 * inserted=false、保持修订号不变，并且仍把该候选从可用行中移除。
 *
 * <p>English: Behavioral regression for the shared TARGET_ALREADY_PRESENT defect. The first
 * reconcile inserts the item, increments the revision, and removes the stale candidate row; a
 * second reconcile for the same receiver with the stale candidate still in the available list
 * must report inserted=false, preserve the revision, and still remove that candidate.
 */
class WorkbenchTargetAdditionContractTest {
    private static final String BLOCK_ID =
            "minecraft:glass_pane";
    private static final String ENTRY_KEY =
            "minecraft:glass_pane";

    @Test
    void firstReconcileInsertsAndRemovesStaleCandidate() {
        WorkbenchDocument<ManagedAuthoringDraft> document =
                WorkbenchDocument.open(List.of());
        WorkbenchDocument.Item<ManagedAuthoringDraft> item =
                item(ENTRY_KEY, BLOCK_ID);
        List<TargetRowView> available = List.of(
                candidateRow("stale-candidate", BLOCK_ID),
                candidateRow("minecraft:stone", "minecraft:stone"));

        WorkbenchTargetAddition.Result<ManagedAuthoringDraft> result =
                WorkbenchTargetAddition.reconcile(
                        document,
                        item,
                        available,
                        BLOCK_ID);

        assertTrue(result.inserted(), "first add must insert the item");
        assertEquals(
                1,
                result.document().revision(),
                "inserting a new target must increment the revision");
        assertTrue(
                result.document().item(ENTRY_KEY).isPresent(),
                "inserted item must be present in the reconciled document");
        assertEquals(
                1,
                result.availableTargets().size(),
                "the stale candidate for the added receiver must be removed");
        assertEquals(
                "minecraft:stone",
                result.availableTargets().get(0)
                        .receiverBlockId()
                        .orElseThrow());
    }

    @Test
    void repeatedReconcileWithStaleCandidateIsIdempotentNoOp() {
        WorkbenchDocument<ManagedAuthoringDraft> document =
                WorkbenchDocument.open(List.of());
        WorkbenchDocument.Item<ManagedAuthoringDraft> item =
                item(ENTRY_KEY, BLOCK_ID);
        List<TargetRowView> available = List.of(
                candidateRow("stale-candidate", BLOCK_ID),
                candidateRow("minecraft:stone", "minecraft:stone"));

        WorkbenchTargetAddition.Result<ManagedAuthoringDraft> first =
                WorkbenchTargetAddition.reconcile(
                        document,
                        item,
                        available,
                        BLOCK_ID);
        assertTrue(first.inserted());
        long revisionAfterInsert = first.document().revision();

        WorkbenchTargetAddition.Result<ManagedAuthoringDraft> second =
                WorkbenchTargetAddition.reconcile(
                        first.document(),
                        item,
                        List.of(
                                candidateRow(
                                        "stale-candidate",
                                        BLOCK_ID),
                                candidateRow(
                                        "minecraft:stone",
                                        "minecraft:stone")),
                        BLOCK_ID);

        assertFalse(
                second.inserted(),
                "repeating the same add must not insert again");
        assertSame(
                first.document(),
                second.document(),
                "an idempotent repeat must preserve the exact document");
        assertEquals(
                revisionAfterInsert,
                second.document().revision(),
                "an idempotent repeat must not increase the revision");
        assertEquals(
                1,
                second.availableTargets().size(),
                "the stale candidate must still be removed on repeat");
        assertEquals(
                "minecraft:stone",
                second.availableTargets().get(0)
                        .receiverBlockId()
                        .orElseThrow());
    }

    private static WorkbenchDocument.Item<ManagedAuthoringDraft> item(
            String entryKey,
            String blockId) {
        ManagedAuthoringDraft draft = new ManagedAuthoringDraft(
                blockId,
                "minecraft:block/glass_pane",
                "minecraft:block/glass_pane",
                ConnectionMethod.CTM,
                ConnectionMethod.CTM,
                false,
                false);
        return new WorkbenchDocument.Item<>(
                entryKey,
                blockId,
                "autoseamblend/fusion/blockstates/"
                        + blockId.substring(blockId.indexOf(':') + 1)
                        + ".json",
                EngineFamily.FUSION,
                Optional.of(draft),
                ConnectionMethod.CTM,
                false,
                false,
                false,
                true,
                true);
    }

    private static TargetRowView candidateRow(
            String entryKey,
            String blockId) {
        return new TargetRowView(
                entryKey,
                blockId,
                Optional.of(blockId),
                Component.literal(blockId),
                ItemStack.EMPTY,
                EngineFamily.FUSION,
                ConnectionMethod.AUTO,
                true,
                false,
                false,
                false,
                false,
                false,
                true);
    }
}
