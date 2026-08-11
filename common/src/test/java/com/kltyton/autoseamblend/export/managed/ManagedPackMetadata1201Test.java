package com.kltyton.autoseamblend.export.managed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.authoring.storage.ManagedPackMetadata;
import com.kltyton.autoseamblend.export.api.ExportSink;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ManagedPackMetadata1201Test {
    @TempDir
    Path temporaryDirectory;

    @Test
    void managedWorkspaceUsesMinecraft1201ResourcePackMetadata() {
        assertMinecraft1201Metadata(new String(
                ManagedPackMetadata.defaultPackMetadata(),
                StandardCharsets.UTF_8));
    }

    @Test
    void exportedPackUsesMinecraft1201ResourcePackMetadata() throws Exception {
        ManagedExportIr ir = new ManagedExportIr(
                1,
                "generation",
                "1.20.1",
                "forge",
                "ctm",
                "1.20.1-1.1.10",
                "0.3.0",
                List.of());
        Path output = temporaryDirectory.resolve("export");

        new ManagedExportWriter().write(
                ir,
                ManagedExportProfile.AUTHORING,
                new ExportSink(output),
                () -> false);

        assertMinecraft1201Metadata(Files.readString(output.resolve("pack.mcmeta")));
    }

    private static void assertMinecraft1201Metadata(String metadata) {
        assertTrue(metadata.contains("\"pack_format\":15")
                || metadata.contains("\"pack_format\": 15"));
        assertFalse(metadata.contains("\"min_format\""));
        assertFalse(metadata.contains("\"max_format\""));
    }
}
