package com.kltyton.autoseamblend.export.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.export.model.ExportDiagnostic;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportRejectedExceptionDiagnosticsTest {
    @Test
    void messageExposesErrorCodeAndSelector() {
        ExportDiagnostic diagnostic = new ExportDiagnostic(
                ExportDiagnostic.Level.ERROR,
                "missing_baked_document",
                "missing native rule document",
                "minecraft:glass_pane");

        ManagedExportDispatcher.ExportRejectedException exception =
                new ManagedExportDispatcher.ExportRejectedException(List.of(diagnostic));

        assertTrue(exception.getMessage().contains("missing_baked_document"));
        assertTrue(exception.getMessage().contains("minecraft:glass_pane"));
        assertEquals(List.of(diagnostic), exception.diagnostics());
    }
}
