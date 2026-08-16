package com.kltyton.autoseamblend.authoring.storage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 中文：AutoSeamBlend Managed 工作区与 Managed 导出的 1.21.1 版本自有 pack 元数据；
 * Loader 只提供布局与激活。
 *
 * English:
 * Version-owned 1.21.1 pack metadata shared by the AutoSeamBlend Managed workspace and
 * Managed exports; a Loader supplies only the layout and activation.
 */
public final class ManagedPackMetadata {
    /**
     * 中文：Minecraft 1.21.1 资源包格式。PackMetadataSection 只接受整数
     * pack_format，没有 min_format/max_format。
     *
     * English: Minecraft 1.21.1 resource-pack format. PackMetadataSection accepts only
     * the integer pack_format and has no min_format/max_format fields.
     */
    public static final int RESOURCE_PACK_FORMAT = 34;

    private ManagedPackMetadata() {}

    public static byte[] defaultPackMetadata() {
        return stringify("AutoSeamBlend Managed")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 中文：序列化 1.21.1 PackMetadataSection 的 pack 对象，供工作区存储与导出共用，
     * 避免两处元数据漂移。
     *
     * English: Serializes the 1.21.1 PackMetadataSection pack object shared by workspace
     * storage and exports, preventing metadata drift between the two writers.
     */
    public static String stringify(String description) {
        Objects.requireNonNull(description, "description");
        return "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": \""
                + escaped(description)
                + "\",\n"
                + "    \"pack_format\": "
                + RESOURCE_PACK_FORMAT
                + "\n"
                + "  }\n"
                + "}\n";
    }

    private static String escaped(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
