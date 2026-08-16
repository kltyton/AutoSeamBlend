package com.kltyton.autoseamblend.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Optional;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：目标添加调和对账契约：首次 reconcile 插入并递增修订且移除候选；携带陈旧候选
 * 的重复 reconcile 是成功空操作，保持修订并仍移除候选。
 *
 * English: Target-addition reconciliation contract: the first reconcile inserts
 * and bumps the revision while removing the candidate; a repeated reconcile with
 * a stale candidate is a successful no-op that preserves the revision and still
 * removes the candidate.
 */
class WorkbenchTargetAdditionContractTest {
    private static final String RECEIVER = "minecraft:glass_pane";
    private static final String OTHER_RECEIVER = "minecraft:stone";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void firstReconcileInsertsIncrementsRevisionAndRemovesCandidate() {
        WorkbenchDocument<TestDraft> document =
                WorkbenchDocument.open(List.of());
        WorkbenchDocument.Item<TestDraft> requested =
                item(RECEIVER);
        List<TargetRowView> available =
                available(RECEIVER, OTHER_RECEIVER);

        WorkbenchTargetAddition.Result<TestDraft> first =
                WorkbenchTargetAddition.reconcile(
                        document,
                        requested,
                        available,
                        RECEIVER);

        assertTrue(
                first.inserted(),
                "first reconcile must insert the requested item");
        assertEquals(
                document.revision() + 1,
                first.document().revision(),
                "first reconcile must increment the revision");
        assertTrue(
                first.document()
                        .item(requested.entryKey())
                        .isPresent(),
                "first reconcile must retain the inserted item");
        assertTrue(
                first.availableTargets().stream()
                        .noneMatch(row -> row.receiverBlockId()
                                .filter(RECEIVER::equals)
                                .isPresent()),
                "first reconcile must remove the receiver from available rows");
        assertEquals(
                1,
                first.availableTargets().size(),
                "only the unrelated candidate must remain available");
    }

    @Test
    void repeatedReconcileWithStaleCandidateIsIdempotent() {
        WorkbenchDocument<TestDraft> document =
                WorkbenchDocument.open(List.of());
        WorkbenchDocument.Item<TestDraft> requested =
                item(RECEIVER);
        List<TargetRowView> available =
                available(RECEIVER, OTHER_RECEIVER);

        WorkbenchTargetAddition.Result<TestDraft> first =
                WorkbenchTargetAddition.reconcile(
                        document,
                        requested,
                        available,
                        RECEIVER);
        WorkbenchTargetAddition.Result<TestDraft> second =
                WorkbenchTargetAddition.reconcile(
                        first.document(),
                        requested,
                        available,
                        RECEIVER);

        assertFalse(
                second.inserted(),
                "repeated reconcile must not insert a duplicate item");
        assertEquals(
                first.document().revision(),
                second.document().revision(),
                "repeated reconcile must preserve the revision");
        assertTrue(
                second.availableTargets().stream()
                        .noneMatch(row -> row.receiverBlockId()
                                .filter(RECEIVER::equals)
                                .isPresent()),
                "repeated reconcile must still remove the stale candidate");
        assertEquals(
                1,
                second.availableTargets().size(),
                "only the unrelated candidate must remain available");
    }

    private static WorkbenchDocument.Item<TestDraft> item(
            String receiver) {
        TestDraft draft = new TestDraft(
                ConnectionMethod.CTM,
                false);
        return new WorkbenchDocument.Item<>(
                receiver,
                receiver,
                "assets/autoseamblend/fusion/blockstates/"
                        + receiver.replace(":", "/")
                        + ".json",
                EngineFamily.FUSION,
                Optional.of(draft),
                draft.requestedMethod(),
                draft.compatibility(),
                false,
                false,
                true,
                true);
    }

    private static List<TargetRowView> available(
            String... receivers) {
        return java.util.Arrays.stream(receivers)
                .map(receiver -> new TargetRowView(
                        receiver,
                        receiver,
                        Optional.of(receiver),
                        Component.literal(receiver),
                        ItemStack.EMPTY,
                        EngineFamily.FUSION,
                        ConnectionMethod.CTM,
                        false,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true))
                .toList();
    }

    private record TestDraft(
            ConnectionMethod requestedMethod,
            boolean compatibility)
            implements WorkbenchDraftFields {}
}
