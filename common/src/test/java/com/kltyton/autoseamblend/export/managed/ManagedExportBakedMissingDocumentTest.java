package com.kltyton.autoseamblend.export.managed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.export.api.ExportSink;
import com.kltyton.autoseamblend.export.model.ExportDiagnostic;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 中文：RED 合同——BAKED 导出对非 NONE 引擎规则必须拒绝“发布 PNG 贴图但零
 * baked 原生文档”的状态，诊断码为 missing_baked_document，且该规则不得写出贴图。
 *
 * <p>English: RED contract -- a BAKED export must reject a non-NONE engine rule
 * that would publish PNG tiles with zero baked native documents, using diagnostic
 * code missing_baked_document, and must not publish the rule's tiles.
 */
class ManagedExportBakedMissingDocumentTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void bakedExportRejectsNonNoneRuleWithTilesButNoBakedDocument()
            throws Exception {
        ManagedExportIr.Rule rule = new ManagedExportIr.Rule(
                0,
                "minecraft:glass_pane",
                "minecraft:glass_pane",
                List.of(new ManagedExportIr.Document(
                        "assets/minecraft/fusion/glass_pane.json",
                        "{\"authoring\":true}"
                                .getBytes(
                                        StandardCharsets.UTF_8),
                        null)),
                "AUTO",
                "CTM",
                List.of("multipart_pane"),
                List.of(0),
                Map.of(0, "DEFAULT"),
                List.of(0),
                List.of(),
                List.of(new ManagedExportIr.Tile(
                        "assets/minecraft/textures/block/glass.png",
                        "assets/minecraft/textures/block/glass.png",
                        0,
                        ManagedExportIr.Tile.Source.GENERATED,
                        new byte[] {1, 2, 3, 4})));
        ManagedExportIr ir = new ManagedExportIr(
                1,
                "generation",
                "1.21.1",
                "neoforge",
                "fusion",
                "1.21.1-1.3.12",
                "1.0.1",
                List.of(rule));
        Path output = temporaryDirectory.resolve("export");
        ExportSink sink = new ExportSink(output);

        ManagedExportWriter.WriteResult result =
                new ManagedExportWriter().write(
                        ir,
                        ManagedExportProfile.BAKED,
                        sink,
                        () -> false);

        assertTrue(
                result.diagnostics().stream()
                        .filter(value -> value.level()
                                == ExportDiagnostic.Level.ERROR)
                        .anyMatch(value -> value.code()
                                .equals("missing_baked_document")),
                "BAKED export of a non-NONE rule without any baked native "
                        + "document must fail with missing_baked_document; got "
                        + result.diagnostics());
        assertFalse(
                Files.exists(output.resolve(
                        "assets/minecraft/textures/block/glass.png")),
                "the orphan PNG tile must not be published when its baked "
                        + "native document is missing");
    }
}
