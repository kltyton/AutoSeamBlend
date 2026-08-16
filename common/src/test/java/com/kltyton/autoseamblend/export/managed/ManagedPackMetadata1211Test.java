package com.kltyton.autoseamblend.export.managed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.authoring.storage.ManagedPackMetadata;
import com.kltyton.autoseamblend.export.api.ExportSink;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 中文：RED 合同——Minecraft 1.21.1 的 PackMetadataSection 只接受
 * description + pack_format:int，资源包格式为 34；当前 Managed 元数据错误写入
 * min_format/max_format 84，必须被本测试捕获。
 *
 * <p>English: RED contract -- Minecraft 1.21.1 PackMetadataSection accepts only
 * description plus pack_format:int and the resource-pack format is 34; the current
 * Managed metadata wrongly writes min_format/max_format 84 and must be caught here.
 */
class ManagedPackMetadata1211Test {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void managedWorkspaceUsesMinecraft1211ResourcePackMetadata() {
        assertMinecraft1211Metadata(new String(
                ManagedPackMetadata.defaultPackMetadata(),
                StandardCharsets.UTF_8));
    }

    @Test
    void exportedPackUsesMinecraft1211ResourcePackMetadata()
            throws Exception {
        ManagedExportIr ir = new ManagedExportIr(
                1,
                "generation",
                "1.21.1",
                "neoforge",
                "fusion",
                "1.21.1-1.3.12",
                "1.0.1",
                List.of());
        Path output = temporaryDirectory.resolve("export");

        new ManagedExportWriter().write(
                ir,
                ManagedExportProfile.AUTHORING,
                new ExportSink(output),
                () -> false);

        assertMinecraft1211Metadata(
                Files.readString(output.resolve("pack.mcmeta")));
    }

    private static void assertMinecraft1211Metadata(
            String metadata) {
        var parsed = PackMetadataSection.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(metadata)
                        .getAsJsonObject()
                        .get("pack"));
        assertTrue(
                parsed.result().isPresent(),
                () -> "Minecraft PackMetadataSection rejected metadata: " + parsed);
        assertTrue(
                metadata.contains("\"pack_format\": 34")
                        || metadata.contains("\"pack_format\":34"),
                "1.21.1 pack metadata must declare pack_format 34: "
                        + metadata);
        assertTrue(
                metadata.contains("description"),
                "1.21.1 PackMetadataSection requires a description");
        assertFalse(
                metadata.contains("\"min_format\""),
                "1.21.1 PackMetadataSection has no min_format field: "
                        + metadata);
        assertFalse(
                metadata.contains("\"max_format\""),
                "1.21.1 PackMetadataSection has no max_format field: "
                        + metadata);
    }
}
