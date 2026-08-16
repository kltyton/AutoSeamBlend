package com.kltyton.autoseamblend.export.managed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.export.api.ExportSink;
import com.kltyton.autoseamblend.export.model.ExportDiagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 中文：BAKED 导出缺陷的 RED 合同。当前 ManagedExportWriter.writeBakedRule 在文档没有
 * baked 视图时直接跳过（document.baked().isEmpty()），仍会把 PNG 瓦片写入资源包，且不会
 * 产生任何诊断。期望：非 NONE 引擎规则只要发布 PNG 瓦片却没有至少一个 baked 原生文档，
 * 就必须以 ERROR missing_baked_document 拒绝，并且不得发布瓦片。
 *
 * <p>English: RED contract for the BAKED-export defect. ManagedExportWriter.writeBakedRule
 * currently skips documents without a baked view yet still writes PNG tiles, emitting no
 * diagnostic. Expected: any non-NONE engine rule that would publish PNG tiles but has zero
 * baked native documents must be rejected with ERROR code missing_baked_document, and tiles
 * must not be published.
 */
class ManagedExportWriterBakedMissingDocumentContractTest {
    private static final byte[] PNG =
            new byte[] {1, 2, 3, 4, 5};
    private static final byte[] AUTHORING =
            "method=ctm\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temp;

    @Test
    void bakedProfileRejectsNonNoneEngineRuleWithTilesButNoBakedDocument()
            throws IOException {
        ManagedExportIr ir = ir("athena", "ctm", "ctm");

        ManagedExportWriter.WriteResult result =
                new ManagedExportWriter().write(
                        ir,
                        ManagedExportProfile.BAKED,
                        new ExportSink(temp),
                        () -> false);

        assertTrue(
                result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.level() == ExportDiagnostic.Level.ERROR
                                && "missing_baked_document".equals(diagnostic.code())),
                "BAKED export must reject a non-NONE engine rule that publishes PNG tiles "
                        + "but zero baked native documents (missing_baked_document)");
        assertFalse(
                result.files().stream()
                        .anyMatch(file -> file.path().endsWith(".png")),
                "tiles must not be published when the baked native document is missing");
    }

    @Test
    void bakedProfileAllowsNoneMethodRuleWithoutBakedDocument()
            throws IOException {
        ManagedExportIr ir = ir("athena", "none", "none");

        ManagedExportWriter.WriteResult result =
                new ManagedExportWriter().write(
                        ir,
                        ManagedExportProfile.BAKED,
                        new ExportSink(temp),
                        () -> false);

        assertFalse(
                result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.level() == ExportDiagnostic.Level.ERROR
                                && "missing_baked_document".equals(diagnostic.code())),
                "a NONE-method rule must stay outside the missing_baked_document rejection");
    }

    @Test
    void bakedProfileRetainsResourcePackMetadata84()
            throws IOException {
        ManagedExportIr ir = ir("athena", "ctm", "ctm");
        ExportSink sink = new ExportSink(temp);

        new ManagedExportWriter().write(
                ir,
                ManagedExportProfile.BAKED,
                sink,
                () -> false);

        String pack = Files.readString(
                sink.root().resolve("pack.mcmeta"),
                StandardCharsets.UTF_8);
        assertTrue(
                pack.contains("\"min_format\": [")
                        && pack.contains("\"max_format\": 84"),
                "26.1.2 resource pack metadata must stay at format 84.0");
        assertFalse(
                pack.contains("101"),
                "resource pack metadata must not drift to the data pack format 101");
    }

    private static ManagedExportIr ir(
            String engine,
            String requestedMethod,
            String resolvedMethod) {
        ManagedExportIr.Rule rule = new ManagedExportIr.Rule(
                0,
                "minecraft:glass",
                "minecraft:glass",
                List.of(new ManagedExportIr.Document(
                        "assets/minecraft/optifine/ctm/glass.properties",
                        AUTHORING,
                        null)),
                requestedMethod,
                resolvedMethod,
                List.of(),
                List.of(0),
                Map.of(),
                List.of(),
                List.of(),
                List.of(new ManagedExportIr.Tile(
                        "assets/minecraft/optifine/ctm/glass_0.png",
                        0,
                        ManagedExportIr.Tile.Source.GENERATED,
                        PNG)));
        return new ManagedExportIr(
                1L,
                "managed-hash",
                "26.1.2",
                "neoforge",
                engine,
                "4.7.3",
                "1.0.1",
                List.of(rule));
    }
}
