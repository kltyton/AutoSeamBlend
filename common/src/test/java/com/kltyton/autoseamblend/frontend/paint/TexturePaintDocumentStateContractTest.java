package com.kltyton.autoseamblend.frontend.paint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：锁定 26.1.2 语义——selectedEditable/canUndo/canRedo 必须反映真实文档槽位证据
 * 与历史栈，不可编辑（UNKNOWN 等）槽位 apply 必须为 no-op。
 *
 * English: Locks the 26.1.2 semantics: selectedEditable/canUndo/canRedo must reflect the
 * real slot evidence and history stacks, and non-editable (UNKNOWN-like) slots must no-op.
 */
class TexturePaintDocumentStateContractTest {

    @Test
    void editableSlotReportsRealEditableAndHistory() {
        // 中文：PRESENT 槽位可编辑；历史栈驱动 canUndo/canRedo。
        // English: A PRESENT slot is editable; history stacks drive canUndo/canRedo.
        TexturePaintDocument document = document(
                NativeSlotIntent.PRESENT,
                new int[]{
                        0xFF000000,
                        0xFF000000,
                        0xFF000000,
                        0xFF000000
                });

        assertTrue(document.selectedEditable());
        assertFalse(document.canUndo());
        assertFalse(document.canRedo());
        assertTrue(document.apply(0, 0));
        assertTrue(document.dirty());
        assertTrue(document.canUndo());
        assertFalse(document.canRedo());
        assertTrue(document.undo());
        assertFalse(document.canUndo());
        assertTrue(document.canRedo());
    }

    @Test
    void protectedSlotIsNotEditableAndApplyIsNoOp() {
        // 中文：UNKNOWN 槽位不可编辑，apply 必须返回 false 且不产生 dirty。
        // English: An UNKNOWN slot is not editable; apply must return false and stay clean.
        TexturePaintDocument document = document(
                NativeSlotIntent.UNKNOWN,
                new int[]{
                        0xFF000000,
                        0xFF000000,
                        0xFF000000,
                        0xFF000000
                });

        assertFalse(document.selectedEditable());
        assertFalse(document.canUndo());
        assertFalse(document.canRedo());
        assertFalse(document.apply(0, 0));
        assertFalse(document.dirty());
    }

    private static TexturePaintDocument document(
            NativeSlotIntent intent,
            int[] pixels) {
        PaintDocumentSource.Slot slot = new PaintDocumentSource.Slot(
                0,
                0,
                "assets/minecraft/textures/block/test.png",
                "carrier:0",
                0,
                0,
                2,
                2,
                intent,
                false,
                pixels);
        return new TexturePaintDocument(
                new PaintDocumentSource(
                        "0 1",
                        List.of(slot)));
    }
}
