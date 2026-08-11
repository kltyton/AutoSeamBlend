package com.kltyton.autoseamblend.frontend.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.frontend.paint.PaintDocumentSource;
import com.kltyton.autoseamblend.frontend.paint.TexturePaintDocument;
import com.kltyton.autoseamblend.frontend.model.PaintViewModel;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/**
 * 中文：锁定 26.1.2 投影语义——PaintViewModel 的 editable/canUndo/canRedo 必须来自真实
 * 文档，禁止硬编码 true（不可编辑槽位必须投影为 false）。
 *
 * English: Locks the 26.1.2 projection semantics: PaintViewModel editable/canUndo/canRedo
 * must derive from the real document and must never be hardcoded true (a non-editable slot
 * must project false).
 */
class WorkbenchViewMappingsPaintProjectionContractTest {

    @Test
    void paintProjectionReportsRealEditableAndHistory() {
        TexturePaintDocument document = new TexturePaintDocument(
                new PaintDocumentSource(
                        "0",
                        List.of(new PaintDocumentSource.Slot(
                                0,
                                0,
                                "assets/minecraft/textures/block/test.png",
                                "carrier:0",
                                0,
                                0,
                                1,
                                1,
                                NativeSlotIntent.UNKNOWN,
                                false,
                                new int[]{0xFF000000}))));

        PaintViewModel projection =
                WorkbenchViewMappings.paint(
                        document,
                        Direction.NORTH,
                        Component.empty());

        assertFalse(projection.editable());
        assertFalse(projection.canUndo());
        assertFalse(projection.canRedo());
    }
}
