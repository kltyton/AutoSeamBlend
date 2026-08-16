package com.kltyton.autoseamblend.export.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 中文：ExportRejectedException 消息契约回归。BAKED 导出因 missing_baked_document 被拒绝时，
 * 异常消息必须同时包含紧凑 ERROR 诊断码与选择器身份，diagnostics() 保持与写入器一致，
 * 且 pack.mcmeta 仍使用 26.1.2 资源包格式 84.0（不得漂移到数据包格式 101）。
 *
 * <p>English: ExportRejectedException message contract regression. When a BAKED export is
 * rejected for missing_baked_document, the exception message must carry both the compact ERROR
 * diagnostic code and the selector identity, diagnostics() must stay identical to the writer's,
 * and pack.mcmeta must remain at the 26.1.2 resource pack format 84.0 (never drifting to data
 * pack format 101).
 */
class ManagedExportDispatcherExportRejectedExceptionContractTest {
    private static final byte[] PNG =
            new byte[] {1, 2, 3, 4, 5};
    private static final byte[] AUTHORING =
            "method=ctm\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temp;

    @Test
    void rejectedExceptionMessageCarriesErrorCodeAndSelectorIdentity()
            throws IOException {
        ManagedExportIr ir = ir("athena", "ctm", "ctm");
        ManagedExportDispatcher dispatcher =
                new ManagedExportDispatcher();
        ManagedExportRequest request = new ManagedExportRequest(
                ManagedExportProfile.BAKED,
                temp.resolve("export"),
                false,
                false);

        ManagedExportDispatcher.ExportRejectedException thrown =
                org.junit.jupiter.api.Assertions.assertThrows(
                        ManagedExportDispatcher.ExportRejectedException.class,
                        () -> dispatcher.dispatch(
                                request,
                                ir,
                                () -> false,
                                ignored -> true));

        assertTrue(
                thrown.getMessage().contains("missing_baked_document"),
                "rejection message must contain the compact ERROR diagnostic code; actual: "
                        + thrown.getMessage());
        assertTrue(
                thrown.getMessage().contains("minecraft:glass_pane"),
                "rejection message must contain the selector identity; actual: "
                        + thrown.getMessage());
        assertEquals(
                1,
                thrown.diagnostics().size(),
                "diagnostics() must keep the original writer diagnostics");
        ExportDiagnostic diagnostic =
                thrown.diagnostics().get(0);
        assertEquals(
                ExportDiagnostic.Level.ERROR,
                diagnostic.level());
        assertEquals(
                "missing_baked_document",
                diagnostic.code());
        assertEquals(
                "minecraft:glass_pane",
                diagnostic.groupId());
    }

    @Test
    void resourcePackMetadataStaysAtFormat84()
            throws IOException {
        ManagedExportIr ir = ir("athena", "ctm", "ctm");
        ExportSink sink = new ExportSink(
                temp.resolve("metadata"));

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
                "26.1.2 resource pack metadata must stay at format 84.0; actual: "
                        + pack);
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
                "minecraft:glass_pane",
                "minecraft:glass_pane",
                List.of(new ManagedExportIr.Document(
                        "assets/minecraft/optifine/ctm/glass_pane.properties",
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
                        "assets/minecraft/optifine/ctm/glass_pane_0.png",
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
