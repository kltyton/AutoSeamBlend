package com.kltyton.autoseamblend.export.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.export.model.ExportDiagnostic;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：ExportRejectedException 消息必须携带 ERROR 码与选择器身份，且诊断列表保持原样。
 *
 * English: ExportRejectedException message must carry the ERROR code and the
 * selector identity, and the diagnostics list stays unchanged.
 */
final class ManagedExportRejectedExceptionContractTest {
    @Test
    void messageIncludesErrorCodeAndSelectorIdentity() {
        List<ExportDiagnostic> diagnostics = List.of(
                new ExportDiagnostic(
                        ExportDiagnostic.Level.ERROR,
                        "missing_baked_document",
                        "non-NONE rule publishes PNG tile(s) but has zero baked native documents: minecraft:glass_pane",
                        "minecraft:glass_pane"));

        ManagedExportDispatcher.ExportRejectedException exception =
                new ManagedExportDispatcher.ExportRejectedException(
                        diagnostics);

        assertTrue(
                exception.getMessage().contains(
                        "missing_baked_document"),
                "message must include the ERROR code; actual: "
                        + exception.getMessage());
        assertTrue(
                exception.getMessage().contains(
                        "minecraft:glass_pane"),
                "message must include the selector identity; actual: "
                        + exception.getMessage());
        assertEquals(
                diagnostics,
                exception.diagnostics(),
                "diagnostics() must remain unchanged");
    }
}
