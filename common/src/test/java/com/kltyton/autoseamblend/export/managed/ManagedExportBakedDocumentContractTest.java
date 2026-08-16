package com.kltyton.autoseamblend.export.managed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.export.api.ExportSink;
import com.kltyton.autoseamblend.export.model.ExportDiagnostic;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 中文：BAKED 导出必须拒绝“非 NONE 引擎规则发布 PNG 瓦片但零 baked 原生文档”的
 * 组合，并给出 missing_baked_document 诊断码。
 *
 * <p>English: A BAKED export must reject a non-NONE engine rule that publishes PNG
 * tile(s) but zero baked native documents, with diagnostic code
 * missing_baked_document.
 */
final class ManagedExportBakedDocumentContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void bakedExportRejectsNonNoneRuleWithTilesButZeroBakedDocuments()
            throws Exception {
        ManagedExportIr.Document authoringOnly = new ManagedExportIr.Document(
                "assets/autoseamblend/fusion/blockstates/glass_pane.json",
                "{}".getBytes(StandardCharsets.UTF_8),
                (byte[]) null);
        ManagedExportIr.Tile tile = new ManagedExportIr.Tile(
                "assets/autoseamblend/textures/generated/ctm/glass_pane/sheet.png",
                List.of(0),
                ManagedExportIr.Tile.Source.GENERATED,
                new byte[] { 1, 2, 3 });
        ManagedExportIr.Rule rule = new ManagedExportIr.Rule(
                0,
                "minecraft:glass_pane",
                "minecraft:glass_pane",
                List.of(authoringOnly),
                "ctm",
                "ctm",
                List.of(),
                List.of(0),
                Map.of(0, "GENERATED"),
                List.of(0),
                List.of(),
                List.of(tile));
        ManagedExportIr ir = new ManagedExportIr(
                1,
                "generation",
                "1.20.1",
                "forge",
                "fusion",
                "1.20.1",
                "1.0.0",
                List.of(rule));

        ManagedExportWriter.WriteResult result = new ManagedExportWriter().write(
                ir,
                ManagedExportProfile.BAKED,
                new ExportSink(temporaryDirectory.resolve("export")),
                () -> false);

        assertTrue(
                result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.level() == ExportDiagnostic.Level.ERROR
                                && "missing_baked_document".equals(diagnostic.code())),
                "non-NONE BAKED export with tiles but zero baked native documents "
                        + "must be rejected with missing_baked_document; actual: "
                        + result.diagnostics());
    }
}
