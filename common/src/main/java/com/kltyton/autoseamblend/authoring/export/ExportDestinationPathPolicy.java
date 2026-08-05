package com.kltyton.autoseamblend.authoring.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 中文：导出目标路径的公共无覆盖策略；目录选择器和实际导出 I/O 留在 Loader 边界。
 *
 * English:
 * Common collision-free export-destination policy; directory selection and
 * actual export I/O remain at the Loader boundary.
 */
public final class ExportDestinationPathPolicy {
    private static final String PACK_DIRECTORY = "AutoSeamBlend-Export";

    private ExportDestinationPathPolicy() {}

    public static Path availableDestination(Path parent) {
        Objects.requireNonNull(parent, "parent");
        Path candidate = parent.resolve(PACK_DIRECTORY);
        for (int suffix = 2; Files.exists(candidate); suffix++) {
            candidate = parent.resolve(PACK_DIRECTORY + '-' + suffix);
        }
        return candidate;
    }

    /**
     * 中文：拒绝导出目标与受管材质包目录互相包含，避免覆盖可编辑源。
     * English: Rejects destinations that contain or are contained by the managed pack so editable
     * sources cannot be overwritten.
     */
    public static void requireOutsideManaged(
            Path destination,
            Path managedRoot) throws IOException {
        Path output = Objects.requireNonNull(destination, "destination")
                .toAbsolutePath()
                .normalize();
        Path managed = Objects.requireNonNull(managedRoot, "managedRoot")
                .toAbsolutePath()
                .normalize();
        if (output.startsWith(managed) || managed.startsWith(output)) {
            throw new IOException("EXPORT_DESTINATION_OVERLAPS_MANAGED");
        }
    }
}
