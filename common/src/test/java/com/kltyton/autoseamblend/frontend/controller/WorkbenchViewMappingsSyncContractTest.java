package com.kltyton.autoseamblend.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.frontend.paint.PaintDocumentSource;
import com.kltyton.autoseamblend.frontend.paint.TexturePaintDocument;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 中文：锁定 26.1.2 syncPaintDocument 语义——脏绘画在 item 已 modified 时必须 touch 使
 * revision+1；未 modified 且有 draft 时 replace 并标记 modified；干净绘画原样返回。
 *
 * English: Locks the 26.1.2 syncPaintDocument semantics: dirty paint with an already
 * modified item must touch (revision+1); an unmodified item with a draft is replaced and
 * marked modified; clean paint returns the source unchanged.
 */
class WorkbenchViewMappingsSyncContractTest {

    @Test
    void dirtyPaintWithAlreadyModifiedItemMustTouchRevision() {
        WorkbenchDocument<TestDraft> source = sourceDocument(true);
        WorkbenchDocument.Item<TestDraft> item =
                source.item("entry").orElseThrow();

        WorkbenchDocument<TestDraft> next =
                WorkbenchViewMappings.syncPaintDocument(
                        source,
                        item,
                        dirtyPaint());

        assertEquals(
                source.revision() + 1,
                next.revision());
        assertTrue(next.dirty());
    }

    @Test
    void dirtyPaintWithUnmodifiedDraftItemReplacesAndMarksModified() {
        WorkbenchDocument<TestDraft> source = sourceDocument(false);
        WorkbenchDocument.Item<TestDraft> item =
                source.item("entry").orElseThrow();

        WorkbenchDocument<TestDraft> next =
                WorkbenchViewMappings.syncPaintDocument(
                        source,
                        item,
                        dirtyPaint());

        assertEquals(
                source.revision() + 1,
                next.revision());
        assertTrue(next.item("entry")
                .orElseThrow()
                .modified());
    }

    @Test
    void cleanPaintLeavesSourceUntouched() {
        WorkbenchDocument<TestDraft> source = sourceDocument(false);
        WorkbenchDocument.Item<TestDraft> item =
                source.item("entry").orElseThrow();

        WorkbenchDocument<TestDraft> next =
                WorkbenchViewMappings.syncPaintDocument(
                        source,
                        item,
                        cleanPaint());

        assertSame(source, next);
    }

    private static WorkbenchDocument<TestDraft> sourceDocument(
            boolean modified) {
        TestDraft draft = new TestDraft(
                ConnectionMethod.CTM,
                true);
        WorkbenchDocument.Item<TestDraft> item =
                new WorkbenchDocument.Item<>(
                        "entry",
                        "entry",
                        "path",
                        EngineFamily.CTM_MOD,
                        Optional.of(draft),
                        draft.requestedMethod(),
                        draft.compatibility(),
                        false,
                        false,
                        false,
                        modified);
        return WorkbenchDocument.open(List.of(item));
    }

    private static TexturePaintDocument dirtyPaint() {
        TexturePaintDocument document = paint();
        document.apply(0, 0);
        return document;
    }

    private static TexturePaintDocument cleanPaint() {
        return paint();
    }

    private static TexturePaintDocument paint() {
        PaintDocumentSource.Slot slot = new PaintDocumentSource.Slot(
                0,
                0,
                "assets/minecraft/textures/block/test.png",
                "carrier:0",
                0,
                0,
                1,
                1,
                NativeSlotIntent.PRESENT,
                false,
                new int[]{0xFF000000});
        return new TexturePaintDocument(
                new PaintDocumentSource(
                        "0",
                        List.of(slot)));
    }

    private record TestDraft(
            ConnectionMethod requestedMethod,
            boolean compatibility)
            implements WorkbenchDraftFields {}
}
