package com.kltyton.autoseamblend.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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

class WorkbenchTargetAdditionTest {
    private static final String BLOCK_ID = "minecraft:glass";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void repeatedAdditionIsIdempotentAndAlwaysEvictsStaleCandidate() {
        WorkbenchDocument<TestDraft> empty = WorkbenchDocument.open(List.of());
        WorkbenchDocument.Item<TestDraft> item = item();
        List<TargetRowView> staleCandidates = List.of(
                row(BLOCK_ID),
                row("minecraft:stone"));

        WorkbenchTargetAddition.Outcome<TestDraft> first =
                WorkbenchTargetAddition.reconcile(
                        empty,
                        item,
                        staleCandidates,
                        BLOCK_ID);

        assertTrue(first.inserted());
        assertEquals(1, first.document().revision());
        assertEquals(
                List.of("minecraft:stone"),
                receivers(first.availableTargets()));

        WorkbenchTargetAddition.Outcome<TestDraft> repeated =
                WorkbenchTargetAddition.reconcile(
                        first.document(),
                        item,
                        staleCandidates,
                        BLOCK_ID);

        assertFalse(repeated.inserted());
        assertSame(first.document(), repeated.document());
        assertEquals(1, repeated.document().revision());
        assertEquals(
                List.of("minecraft:stone"),
                receivers(repeated.availableTargets()));
    }

    private static WorkbenchDocument.Item<TestDraft> item() {
        TestDraft draft = new TestDraft(ConnectionMethod.AUTO, true);
        return new WorkbenchDocument.Item<>(
                "new:fusion:" + BLOCK_ID,
                BLOCK_ID,
                "",
                EngineFamily.FUSION,
                Optional.of(draft),
                draft.requestedMethod(),
                draft.compatibility(),
                false,
                false,
                true,
                true);
    }

    private static TargetRowView row(String blockId) {
        return new TargetRowView(
                blockId,
                blockId,
                Optional.of(blockId),
                Component.literal(blockId),
                ItemStack.EMPTY,
                EngineFamily.FUSION,
                ConnectionMethod.AUTO,
                true,
                false,
                false,
                true,
                true,
                true,
                true);
    }

    private static List<String> receivers(List<TargetRowView> rows) {
        return rows.stream()
                .flatMap(row -> row.receiverBlockId().stream())
                .toList();
    }

    private record TestDraft(
            ConnectionMethod requestedMethod,
            boolean compatibility)
            implements WorkbenchDraftFields {}
}
